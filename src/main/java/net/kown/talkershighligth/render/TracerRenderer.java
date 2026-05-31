package net.kown.talkershighligth.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.kown.talkershighligth.config.TracerConfig;
import net.kown.talkershighligth.tracer.TracerEntry;
import net.kown.talkershighligth.tracer.TracerManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.Collection;
import java.util.UUID;

/**
 * Renders a coloured line from the camera/crosshair toward each player who is
 * currently speaking in Simple Voice Chat.
 *
 * <h3>Colour gradient</h3>
 * The line colour is linearly interpolated from {@link TracerConfig#getLowVolumeColor()}
 * (quiet / just-started talking) to {@link TracerConfig#getHighVolumeColor()} (loud /
 * peak activity) based on the player's smoothed amplitude in {@link TracerEntry}.
 *
 * <h3>Line thickness</h3>
 * {@link RenderSystem#lineWidth} is used; note that many modern GPU drivers
 * cap this at 1.0 px in OpenGL core-profile mode.  Phase 2 can replace this
 * with quad-based billboard lines for a more reliable thick-line effect.
 *
 * <h3>Render hook</h3>
 * We use {@code WorldRenderEvents.AFTER_TRANSLUCENT} so the lines composite
 * over the world but before the HUD.  Depth testing is temporarily disabled
 * so tracers show through walls (ESP-style).
 */
public final class TracerRenderer {

    private TracerRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(TracerRenderer::renderTracers);
    }

    // ── Main render callback ───────────────────────────────────────────────────

    private static void renderTracers(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        TracerConfig config = TracerConfig.INSTANCE;
        if (!config.enabled) return;

        Collection<TracerEntry> entries = TracerManager.INSTANCE.getActiveTracers();
        if (entries.isEmpty()) return;

        MatrixStack matrices = ctx.matrixStack();
        if (matrices == null) return;   // Should not happen for AFTER_TRANSLUCENT, but guard anyway.

        Camera camera   = ctx.camera();
        Vec3d  camPos   = camera.getPos();

        // Translate the matrix stack so world-space coordinates can be used directly.
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        Matrix4f posMatrix    = matrices.peek().getPositionMatrix();

        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
        RenderSystem.disableDepthTest();   // show through walls (ESP)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (TracerEntry entry : entries) {
            UUID playerUUID = entry.getPlayerUUID();

            // Skip self (shouldn't normally occur, but be safe).
            if (playerUUID.equals(client.player.getUuid())) continue;

            AbstractClientPlayerEntity player = findPlayer(client, playerUUID);
            if (player == null) continue;

            float amplitude = entry.getSmoothedAmplitude();

            // ── Geometry ─────────────────────────────────────────────────────
            // Start at the camera eye position (already in world-space thanks to our translate).
            double fromX = camPos.x;
            double fromY = camPos.y;
            double fromZ = camPos.z;

            // Target: vertical centre-mass of the player model.
            double toX = player.getX();
            double toY = player.getY() + player.getHeight() * 0.5;
            double toZ = player.getZ();

            // Normal vector along the line (required by VertexFormats.LINES).
            double dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-6) continue;   // player is at the camera, skip.
            float nx = (float)(dx / len);
            float ny = (float)(dy / len);
            float nz = (float)(dz / len);

            // ── Appearance ───────────────────────────────────────────────────
            float[] c = lerpColor(config, amplitude);
            float lineWidth = lerpF(config.minLineWidth, config.maxLineWidth, amplitude);

            RenderSystem.lineWidth(lineWidth);

            // ── Draw ─────────────────────────────────────────────────────────
            Tessellator   tess   = Tessellator.getInstance();
            BufferBuilder buffer = tess.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);

            buffer.vertex(posMatrix, (float) fromX, (float) fromY, (float) fromZ)
                    .color(c[0], c[1], c[2], c[3])
                    .normal(nx, ny, nz);

            buffer.vertex(posMatrix, (float) toX, (float) toY, (float) toZ)
                    .color(c[0], c[1], c[2], c[3])
                    .normal(nx, ny, nz);

            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }

        // ── Restore GL state ──────────────────────────────────────────────────
        matrices.pop();
        RenderSystem.enableDepthTest();
        RenderSystem.lineWidth(1.0f);
        RenderSystem.disableBlend();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Finds a loaded player entity by UUID, or {@code null} if not found. */
    private static AbstractClientPlayerEntity findPlayer(MinecraftClient client, UUID uuid) {
        for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
            if (p.getUuid().equals(uuid)) return p;
        }
        return null;
    }

    /**
     * Linearly interpolates the RGBA colour between
     * {@link TracerConfig#getLowVolumeColor()} (t=0) and
     * {@link TracerConfig#getHighVolumeColor()} (t=1).
     *
     * @return {@code [r, g, b, a]} each in [0.0, 1.0].
     */
    private static float[] lerpColor(TracerConfig cfg, float t) {
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