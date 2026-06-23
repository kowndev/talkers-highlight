package net.kown.talkershighligth.utils;

import net.kown.talkershighligth.config.Config;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

/**
 * Renders a small status icon in the bottom-left of the screen while
 * connected to a server (or in any world). It is NOT hidden by the
 * F3+F1 "hide HUD" toggle, because HudRenderCallback fires outside of
 * the vanilla hudHidden-gated render path.
 *
 * The icon's appearance reflects whether
 * is on or off — swap that field/check for whatever your actual feature
 * flag ends up being.
 */
public final class HUDoverlay {

    // Replace "yourmod" with your actual mod id, and make sure this
    // texture exists at:
    //   src/main/resources/assets/yourmod/textures/gui/feature_icon.png
    private static final Identifier ICON =
            Identifier.of("talkershighligth", "feature_icon.png");

    // Icon size in pixels. Adjust to match your texture's actual dimensions.
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

        // Only draw while actually in a world (covers both singleplayer
        // and connected-to-server cases).
        if (client.player == null || client.world == null) {
            return;
        }

        // Don't draw over other full-screen GUIs (inventory, chat input
        // box focus state still renders fine since this draws every frame
        // regardless — but skip on things like the pause/options screen
        // if you want it hidden there; remove this check if you want it
        // visible even on the pause screen).
        if (client.currentScreen != null) {
            return;
        }

        // Only draw at all when the feature is on. Off = no icon, nothing drawn.
        if (!Config.INSTANCE.TracerEnabled && !Config.INSTANCE.HighlightEnabled) {
            return;
        }

        int screenHeight = context.getScaledWindowHeight();

        int x = MARGIN_X;
        int y = screenHeight - ICON_SIZE - MARGIN_Y;

        // DrawContext#drawTexture(Identifier texture, int x, int y, float u, float v,
        //                          int width, int height, int textureWidth, int textureHeight)
        // — this is the correct overload for yarn 1.21.1+build.3; the
        // Function<Identifier, RenderLayer> first-parameter variant was
        // introduced in later 1.21.x versions, not 1.21.1.
        context.drawTexture(
                ICON,
                x, y,
                0.0f, 0.0f,
                ICON_SIZE, ICON_SIZE,
                ICON_SIZE, ICON_SIZE
        );
    }
}