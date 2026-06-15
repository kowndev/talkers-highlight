package net.kown.talkershighligth.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.kown.talkershighligth.TalkersHighlightClient;
import net.kown.talkershighligth.config.TracerConfig;
import net.kown.talkershighligth.tracer.TracerEntry;
import net.kown.talkershighligth.tracer.TracerManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.kown.talkershighligth.utils.NameUUIDSearch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.Collection;
import java.util.UUID;

public final class TracerRenderer {

    private TracerRenderer() {}

    public static void register() {
//        WorldRenderEvents.AFTER_TRANSLUCENT.register(TracerRenderer::renderTracers);
        WorldRenderEvents.LAST.register(TracerRenderer::renderTracers);
    }

    private static void renderTracers(WorldRenderContext ctx) {

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        TracerConfig config = TracerConfig.INSTANCE;
        if (!config.enabled) return;

        Collection<TracerEntry> entries = TracerManager.INSTANCE.getActiveTracers();
        if (entries.isEmpty()) return;

        MatrixStack matrices = ctx.matrixStack();
        if (matrices == null) return;

        Camera camera = ctx.camera();
        Vec3d camPos = camera.getPos();

        // ── NEW: compute the crosshair "look" direction from the camera rotation ──
        float yaw   = camera.getYaw();
        float pitch = camera.getPitch();

        double yawRad   = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        // Minecraft's yaw: 0° = south (+Z), 90° = west (-X), etc.
        float lookX = (float)(-Math.sin(yawRad) * Math.cos(pitchRad));
        float lookY = (float)(-Math.sin(pitchRad));
        float lookZ = (float)( Math.cos(yawRad) * Math.cos(pitchRad));

        // Place the line start a small distance in front of the camera along the look ray.
        // This anchors it visually to the crosshair.
        float nearDist = 0.25f; // tweak if needed
        float startX = lookX * nearDist;
        float startY = lookY * nearDist;
        float startZ = lookZ * nearDist;
        // ─────────────────────────────────────────────────────────────────────────

        VertexConsumerProvider.Immediate provider =
                (VertexConsumerProvider.Immediate) ctx.consumers();
        if (provider == null) return;

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (TracerEntry entry : entries) {
            UUID playerUUID = entry.getPlayerUUID();

            if (playerUUID.equals(client.player.getUuid())) continue;

            AbstractClientPlayerEntity player = findPlayer(client, playerUUID);
            if (player == null) continue;

            float amplitude = entry.getSmoothedAmplitude();

            double toX = player.getX();
            double toY = player.getY() + player.getHeight() * 0.5;
            double toZ = player.getZ();

            // Direction from camera to target (for the normal)
            double dx = toX - camPos.x;
            double dy = toY - camPos.y;
            double dz = toZ - camPos.z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-6) continue;
            float nx = (float)(dx / len);
            float ny = (float)(dy / len);
            float nz = (float)(dz / len);

            float[] c = lerpColor(config, amplitude);
            float lineWidth = lerpF(config.minLineWidth, config.maxLineWidth, amplitude);

            RenderSystem.lineWidth(lineWidth);

            VertexConsumer lines = provider.getBuffer(RenderLayer.LINES);

            // ── CHANGED: start vertex is now along the crosshair look ray ──
            lines.vertex(matrices.peek().getPositionMatrix(),
                            startX + (float)camPos.x, startY + (float)camPos.y, startZ + (float)camPos.z)
                    .color(c[0], c[1], c[2], c[3])
                    .normal(matrices.peek(), nx, ny, nz);
            // ───────────────────────────────────────────────────────────────

            lines.vertex(matrices.peek().getPositionMatrix(),
                            (float) toX, (float) toY, (float) toZ)
                    .color(c[0], c[1], c[2], c[3])
                    .normal(matrices.peek(), nx, ny, nz);

            provider.drawCurrentLayer();
        }

        matrices.pop();
        RenderSystem.enableDepthTest();
        RenderSystem.lineWidth(1.0f);
        RenderSystem.disableBlend();
    }

    private static AbstractClientPlayerEntity findPlayer(MinecraftClient client, UUID uuid) {
        assert client.world != null;
        for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
            if (p.getUuid().equals(uuid)) return p;
        }
        return null;
    }

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