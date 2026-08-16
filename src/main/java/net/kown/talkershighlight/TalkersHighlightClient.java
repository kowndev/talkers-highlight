package net.kown.talkershighlight;

import net.kown.talkershighlight.config.Config;
import net.kown.talkershighlight.config.ConfigScreen;
import net.kown.talkershighlight.logger.LoggerManager;
import net.kown.talkershighlight.render.HighlightRenderer;
import net.kown.talkershighlight.render.TracerRenderer;
import net.kown.talkershighlight.manage.ActivityManager;
import net.kown.talkershighlight.utils.HUDoverlay;
import net.kown.talkershighlight.utils.LoggerCall;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TalkersHighlightClient implements ClientModInitializer {

    public static final String MOD_ID = "talkershighlight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static ScheduledExecutorService autosaveExecutor;

    public static KeyBinding toggleHighlightKey;
    public static KeyBinding toggleTracerKey;
    public static KeyBinding configKey;

    @Override
    public void onInitializeClient() {

        // 1. Load persisted config ─────────────────────────────────────────────
        Config.load();
        LOGGER.info("[TH] Config loaded from disk.");

        // 2. Register world-render hook ────────────────────────────────────────
        TracerRenderer.register();
        HighlightRenderer.register();
        HUDoverlay.register();
        registerCommands();

        // 3. Register keybindings ─────────────────────────────────────────────
        toggleTracerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.talkershighlight.toggle_tracer",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.talkershighlight"
        ));

        toggleHighlightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.talkershighlight.toggle_highlight",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.talkershighlight"
        ));

        configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.talkershighlight.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.talkershighlight"
        ));

        // 4. Tick handler ──────────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        LOGGER.info("[TH] Initialised. Press J to toggle, K for settings.");


        // Prints the resolved path for logs .minecraft folder
        LOGGER.info("[THLogger] Writing logs to: {}",
                FabricLoader.getInstance().getGameDir().resolve("thlogger").toAbsolutePath());

        startAutosave();

        // 2. Primary final flush: leaving a world/server.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LoggerManager.writeFinalLog());

        // 3. Backup: quitting the game itself without ever disconnecting from a world.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            stopAutosave();
            LoggerManager.writeFinalLog();
        });

        // 4. Backup: safe even if it races with #2/#3 since writeFinalLog() is synchronized.
        Runtime.getRuntime().addShutdownHook(
                new Thread(LoggerManager::writeFinalLog, "THLogger-ShutdownHook"));
    }

    // ── Per-tick logic ────────────────────────────────────────────────────────

    private void onTick(MinecraftClient client) {

        // ── World management ──────────────────────────────────────────────────
        if (client.world == null) {
            ActivityManager.INSTANCE.clear();
            return;
        }

        // Collect UUIDs of every player currently loaded in the client world.
        Set<UUID> onlineUUIDs = client.world.getPlayers()
                .stream()
                .map(AbstractClientPlayerEntity::getUuid)
                .collect(Collectors.toSet());

        // Decay amplitudes and purge stale / offline tracers.
        ActivityManager.INSTANCE.tick(onlineUUIDs);

        // ── Keybind: toggle ───────────────────────────────────────────────────
        while (toggleTracerKey.wasPressed() || toggleHighlightKey.wasPressed()) {
            Config.INSTANCE.TracerEnabled = !Config.INSTANCE.TracerEnabled;
            Config.INSTANCE.HighlightEnabled = !Config.INSTANCE.HighlightEnabled;
            Config.save();

            if (client.player != null) {
                boolean TracerOn = Config.INSTANCE.TracerEnabled;
                client.player.sendMessage(
                        Text.literal("[TH] Tracer " + (TracerOn ? "§aEnabled" : "§cDisabled")),
                        /* overlay = */ true
                );
                boolean HighlightOn = Config.INSTANCE.HighlightEnabled;
                client.player.sendMessage(
                        Text.literal("[TH] Highlighter " + (HighlightOn ? "§aEnabled" : "§cDisabled")),
                        /* overlay = */ true
                );
            }
        }

        // ── Keybind: config screen ────────────────────────────────────────────
        while (configKey.wasPressed()) {
            if (client.currentScreen == null) {      // don't stack screens
                client.setScreen(ConfigScreen.createScreen(null));
            }
        }
    }

    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LoggerCall.register(dispatcher);
        });
    }
    private static void startAutosave() {
        autosaveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "THLogger-Autosave");
            t.setDaemon(true); // never blocks JVM exit
            return t;
        });
        Config cfg = Config.INSTANCE;
        int intervalSeconds = Math.max(1, cfg.autosaveIntervalSeconds);
        autosaveExecutor.scheduleAtFixedRate(
                LoggerManager::writeAutosave,
                intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    private static void stopAutosave() {
        if (autosaveExecutor != null) {
            autosaveExecutor.shutdownNow();
            autosaveExecutor = null;
        }
    }
}