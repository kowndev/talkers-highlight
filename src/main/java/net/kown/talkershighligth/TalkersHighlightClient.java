package net.kown.talkershighligth;

import net.kown.talkershighligth.config.Config;
import net.kown.talkershighligth.config.ConfigScreen;
import net.kown.talkershighligth.logger.LoggerManager;
import net.kown.talkershighligth.render.TracerRenderer;
import net.kown.talkershighligth.tracer.TracerManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.kown.talkershighligth.utils.HUDoverlay;
import net.kown.talkershighligth.utils.LoggerCall;
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

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

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
    private static ScheduledExecutorService autosaveExecutor;

    /** Default J – toggle tracer overlay on/off. */
    public static KeyBinding toggleKey;

    /** Default K – open YACL config screen. */
    public static KeyBinding configKey;

    @Override
    public void onInitializeClient() {

        // 1. Load persisted config ─────────────────────────────────────────────
        Config.load();
        LOGGER.info("[TH] Config loaded from disk.");

        // 2. Register world-render hook ────────────────────────────────────────
        TracerRenderer.register();
        HUDoverlay.register();
        registerCommands();

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


        // Prints the *actual* resolved path - check logs/latest.log for this
        // line if files seem to be missing. This is the directory FabricLoader
        // believes is the game dir for THIS launch, which can differ from a
        // global .minecraft folder if you're using a per-instance launcher
        // (Prism, MultiMC, CurseForge, ATLauncher, Modrinth App, etc).
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
            Config.INSTANCE.TracerEnabled = !Config.INSTANCE.TracerEnabled;
            Config.save();

            if (client.player != null) {
                boolean on = Config.INSTANCE.TracerEnabled;
                client.player.sendMessage(
                        Text.literal("[TH] Tracer " + (on ? "§aEnabled" : "§cDisabled")),
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