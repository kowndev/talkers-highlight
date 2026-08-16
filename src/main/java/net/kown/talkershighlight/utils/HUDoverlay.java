package net.kown.talkershighlight.utils;

import net.kown.talkershighlight.config.Config;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

public final class HUDoverlay {

    private static final Identifier ICON =
            Identifier.of("talkershighlight", "feature_icon.png");

    private static final int ICON_SIZE = 15;

    // Pixels of padding from the screen edges.
    private static final int MARGIN_X = 6;
    private static final int MARGIN_Y = 6;

    private HUDoverlay() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(HUDoverlay::onHudRender);
    }

    private static void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || client.world == null) {
            return;
        }

        // Make overlay don't draw over GUIs, skip pause/options screen
        if (client.currentScreen != null) {
            return;
        }

        if (!Config.INSTANCE.TracerEnabled && !Config.INSTANCE.HighlightEnabled) {
            return;
        }

        int y = context.getScaledWindowHeight() - ICON_SIZE - MARGIN_Y;

        // DrawContext#drawTexture(Identifier texture, int x, int y, float u, float v,
        //                          int width, int height, int textureWidth, int textureHeight)
        context.drawTexture(
                ICON,
                MARGIN_X, y,
                0.0f, 0.0f,
                ICON_SIZE, ICON_SIZE,
                ICON_SIZE, ICON_SIZE
        );
    }
}