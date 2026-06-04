package net.kown.talkershighligth;

import net.kown.talkershighligth.config.TracerConfig;
import net.kown.talkershighligth.config.TracerConfigScreen;
import net.kown.talkershighligth.render.TracerRenderer;
import net.kown.talkershighligth.tracer.TracerManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Client-side initialisation entry point.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Load config from disk.</li>
 *   <li>Register the world-render hook (tracers).</li>
 *   <li>Register keybindings and their tick handlers.</li>
 *   <li>Drive {@link TracerManager#tick} once per game tick.</li>
 * </ul>
 *
 * <h3>Keybindings (defaults)</h3>
 * <table>
 *   <tr><th>Key</th><th>Action</th></tr>
 *   <tr><td>J</td><td>Toggle tracer on/off (shows actionbar feedback)</td></tr>
 *   <tr><td>K</td><td>Open YACL config screen</td></tr>
 * </table>
 */
public class TalkersHighlightClient implements ClientModInitializer {

    public static final String MOD_ID = "talkershighlight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Default J – toggle tracer overlay on/off. */
    public static KeyBinding toggleKey;

    /** Default K – open YACL config screen. */
    public static KeyBinding configKey;

    @Override
    public void onInitializeClient() {

        // 1. Load persisted config ─────────────────────────────────────────────
        TracerConfig.load();
        LOGGER.info("[TH] Config loaded from disk.");

        // 2. Register world-render hook ────────────────────────────────────────
        TracerRenderer.register();

        // 3. Register keybindings ─────────────────────────────────────────────
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.voicechat_tracer.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.voicechat_tracer"
        ));

        configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.voicechat_tracer.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.voicechat_tracer"
        ));

        // 4. Tick handler ──────────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        LOGGER.info("[TH] Initialised. Press J to toggle, K for settings.");
    }

    // ── Per-tick logic ────────────────────────────────────────────────────────

    private void onTick(MinecraftClient client) {

        // ── World management ──────────────────────────────────────────────────
        if (client.world == null) {
            TracerManager.INSTANCE.clear();
            return;
        }

        // Collect UUIDs of every player currently loaded in the client world.
        Set<UUID> onlineUUIDs = client.world.getPlayers()
                .stream()
                .map(AbstractClientPlayerEntity::getUuid)
                .collect(Collectors.toSet());

        // Decay amplitudes and purge stale / offline tracers.
        TracerManager.INSTANCE.tick(onlineUUIDs);

        // ── Keybind: toggle ───────────────────────────────────────────────────
        while (toggleKey.wasPressed()) {
            TracerConfig.INSTANCE.enabled = !TracerConfig.INSTANCE.enabled;
            TracerConfig.save();

            if (client.player != null) {
                boolean on = TracerConfig.INSTANCE.enabled;
                client.player.sendMessage(
                        Text.literal("[TH] Tracer " + (on ? "§aEnabled" : "§cDisabled")),
                        /* overlay = */ true
                );
            }
        }

        // ── Keybind: config screen ────────────────────────────────────────────
        while (configKey.wasPressed()) {
            if (client.currentScreen == null) {      // don't stack screens
                client.setScreen(TracerConfigScreen.createScreen(null));
            }
        }
    }
}