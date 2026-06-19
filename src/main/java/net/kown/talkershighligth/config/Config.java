package net.kown.talkershighligth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All user-facing settings for VoiceChat Tracer.
 * Values are stored as primitives so Gson can serialise them without adapters.
 * Use {@link #getLowVolumeColor()} / {@link #getHighVolumeColor()} to get
 * {@link java.awt.Color} objects for rendering.
 */
public class Config {

    // ── Singleton ─────────────────────────────────────────────────────────────
    public static Config INSTANCE = new Config();

    private static final Logger LOGGER = LoggerFactory.getLogger("talkershighligth/config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("talkershighligth.json");

    // ── General ───────────────────────────────────────────────────────────────
    /** Master on/off switch for the overlay. */
    public boolean TracerEnabled = true;
    public boolean HighlightEnabled = false;

    // ── Activation thresholds ─────────────────────────────────────────────────
    /**
     * Normalised amplitude [0.0 – 1.0] that a player must exceed before a
     * tracer line is drawn for them.  Raise this to ignore quiet background
     * noise picked up by open-mic setups.
     */
    public float minVolume = 0.05f;
    //public float minVolume = 0.1f;
    /**
     * How long (ms) a tracer stays visible after the last audio packet
     * was received from that player.
     */
    public int tracerPersistMs = 2000;

    // ── Line appearance ───────────────────────────────────────────────────────
    /** Line thickness (pixels) at {@link #minVolume}. */
    public float minLineWidth = 1.0f;

    /** Line thickness (pixels) at maximum detected amplitude. */
    public float maxLineWidth = 3.5f;

    /**
     * ARGB integer for the "quiet" end of the colour gradient.
     * Default: semi-transparent green.
     */
    public int lowVolumeColor = new Color(0, 230, 0, 220).getRGB();

    /**
     * ARGB integer for the "loud" end of the colour gradient.
     * Default: semi-transparent red.
     */
    public int highVolumeColor = new Color(230, 0, 0, 220).getRGB();

    /**
     * Logger list size
     * Logger auto save interval
     */
    public int listSize = 10;
    public int autosaveIntervalSeconds = 30;

    // ── Helpers ───────────────────────────────────────────────────────────────
    public Color getLowVolumeColor()  { return new Color(lowVolumeColor,  true); }
    public Color getHighVolumeColor() { return new Color(highVolumeColor, true); }

    // ── Persistence ───────────────────────────────────────────────────────────
    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            INSTANCE = new Config();
            save();
            return;
        }
        try {
            String json = Files.readString(CONFIG_FILE);
            Config loaded = GSON.fromJson(json, Config.class);
            INSTANCE = (loaded != null) ? loaded : new Config();
        } catch (IOException | com.google.gson.JsonParseException e) {
            LOGGER.warn("[TH] Failed to load config, using defaults.", e);
            INSTANCE = new Config();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            LOGGER.error("[TH] Failed to save config.", e);
        }
    }
}