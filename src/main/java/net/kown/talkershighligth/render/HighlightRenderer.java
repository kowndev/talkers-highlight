package net.kown.talkershighligth.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.kown.talkershighligth.config.Config;
import net.kown.talkershighligth.manage.ActivityEntry;
import net.kown.talkershighligth.manage.ActivityManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.Collection;
import java.util.UUID;

/**
 * Draws a wireframe (non-filled) box outline around any player currently
 * registered in {@link ActivityManager} — i.e. anyone talking, or
 * fading out after talking. Purely a visual indicator: it traces the
 * player's hitbox edges only, it never covers/fills the model.
 *
 * Colour and thickness are driven by the same smoothed amplitude used by
 * {@link TracerRenderer}, so both effects move together.
 */
public final class HighlightRenderer {

    private HighlightRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(HighlightRenderer::renderHighlights);
    }

    private static void renderHighlights(WorldRenderContext ctx) {

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        Config config = Config.INSTANCE;
        if (!config.HighlightEnabled) return;

        Collection<ActivityEntry> entries = ActivityManager.INSTANCE.getActiveEntries();
        if (entries.isEmpty()) return;

        MatrixStack matrices = ctx.matrixStack();
        if (matrices == null) return;

        Vec3d camPos = ctx.camera().getPos();

        VertexConsumerProvider.Immediate provider =
                (VertexConsumerProvider.Immediate) ctx.consumers();
        if (provider == null) return;

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (ActivityEntry entry : entries) {
            UUID playerUUID = entry.getPlayerUUID();

            if (playerUUID.equals(client.player.getUuid())) continue;

            AbstractClientPlayerEntity player = findPlayer(client, playerUUID);
            if (player == null) continue;

            float amplitude = entry.getSmoothedAmplitude();

            float[] c = lerpColor(config, amplitude);
            float outlineWidth = lerpF(config.minLineWidth, config.maxLineWidth, amplitude);

            // Slightly inflate the hitbox so the outline doesn't z-fight with
            // the skin/armor model.
            Box box = player.getBoundingBox().expand(0.02);

            // lineWidth is GL state read at draw-call time, not at vertex-emit
            // time. Since each player can have a different amplitude (and
            // therefore a different width), we flush per-player rather than
            // batching everyone into one draw call — otherwise only the last
            // player's width would apply to every box drawn this frame.
            RenderSystem.lineWidth(outlineWidth);
            VertexConsumer lines = provider.getBuffer(RenderLayer.LINES);

            drawBoxOutline(matrices, lines, box, c);

            // Flush the LINES layer specifically — NOT drawCurrentLayer(),
            // which flushes whatever RenderLayer was last requested ANYWHERE
            // in the frame (including by other mods, e.g. a talking-bubbles
            // overlay that buffers a textured quad via the same shared
            // Immediate provider). Using drawCurrentLayer() here could flush
            // someone else's buffered geometry instead of ours, producing
            // stray textured quads instead of our line boxes.
            provider.draw(RenderLayer.LINES);
        }

        matrices.pop();
        RenderSystem.enableDepthTest();
        RenderSystem.lineWidth(1.0f);
        RenderSystem.disableBlend();
    }

    /**
     * Emits the 12 edges of an axis-aligned box as GL lines (RenderLayer.LINES
     * expects line *segments*, so each edge is its own vertex pair — no
     * shared-vertex line-strip trickery needed).
     */
    private static void drawBoxOutline(MatrixStack matrices, VertexConsumer buffer, Box box, float[] c) {
        var posMatrix = matrices.peek().getPositionMatrix();
        var entry = matrices.peek();

        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;

        // Bottom face (4 edges)
        edge(buffer, posMatrix, entry, c, minX, minY, minZ, maxX, minY, minZ);
        edge(buffer, posMatrix, entry, c, maxX, minY, minZ, maxX, minY, maxZ);
        edge(buffer, posMatrix, entry, c, maxX, minY, maxZ, minX, minY, maxZ);
        edge(buffer, posMatrix, entry, c, minX, minY, maxZ, minX, minY, minZ);

        // Top face (4 edges)
        edge(buffer, posMatrix, entry, c, minX, maxY, minZ, maxX, maxY, minZ);
        edge(buffer, posMatrix, entry, c, maxX, maxY, minZ, maxX, maxY, maxZ);
        edge(buffer, posMatrix, entry, c, maxX, maxY, maxZ, minX, maxY, maxZ);
        edge(buffer, posMatrix, entry, c, minX, maxY, maxZ, minX, maxY, minZ);

        // Vertical edges (4)
        edge(buffer, posMatrix, entry, c, minX, minY, minZ, minX, maxY, minZ);
        edge(buffer, posMatrix, entry, c, maxX, minY, minZ, maxX, maxY, minZ);
        edge(buffer, posMatrix, entry, c, maxX, minY, maxZ, maxX, maxY, maxZ);
        edge(buffer, posMatrix, entry, c, minX, minY, maxZ, minX, maxY, maxZ);
    }

    private static void edge(
            VertexConsumer buffer,
            org.joml.Matrix4f posMatrix,
            MatrixStack.Entry entry,
            float[] c,
            float x1, float y1, float z1,
            float x2, float y2, float z2
    ) {
        // Normal points along the edge direction; for thin unlit lines this
        // is sufficient — RenderLayer.LINES does not depend on it for shading.
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6f) { nx /= len; ny /= len; nz /= len; }

        buffer.vertex(posMatrix, x1, y1, z1).color(c[0], c[1], c[2], c[3]).normal(entry, nx, ny, nz);
        buffer.vertex(posMatrix, x2, y2, z2).color(c[0], c[1], c[2], c[3]).normal(entry, nx, ny, nz);
    }

    private static AbstractClientPlayerEntity findPlayer(MinecraftClient client, UUID uuid) {
        assert client.world != null;
        for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
            if (p.getUuid().equals(uuid)) return p;
        }
        return null;
    }

    private static float[] lerpColor(Config cfg, float t) {
        t = Math.max(0f, Math.min(1f, t));
        Color lo = cfg.getLowVolumeColor();
        Color hi = cfg.getHighVolumeColor();
        return new float[]{
                lerpF(lo.getRed()   / 255f, hi.getRed()   / 255f, t),
                lerpF(lo.getGreen() / 255f, hi.getGreen() / 255f, t),
                lerpF(lo.getBlue()  / 255f, hi.getBlue()  / 255f, t),
                lerpF(lo.getAlpha() / 255f, hi.getAlpha() / 255f, t),
        };
    }

    private static float lerpF(float a, float b, float t) { return a + (b - a) * t; }

}
