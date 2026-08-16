package net.kown.talkershighlight.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

    // Values are stored as primitives so Gson can serialise them without adapters.
public class Config {

    public static Config INSTANCE = new Config();

    private static final Logger LOGGER = LoggerFactory.getLogger("talkershighlight/config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("talkershighlight.json");

    public boolean TracerEnabled = true;
    public boolean HighlightEnabled = false;

    public float minVolume = 0.1f;
    public int PersistanceMs = 2000;

    // in pixels
    public float minLineWidth = 1.0f;
    public float maxLineWidth = 3.5f;

    public int lowVolumeColor = new Color(0, 230, 0, 220).getRGB();
    public int highVolumeColor = new Color(230, 0, 0, 220).getRGB();

    //Logger list size & auto save interval
    public int listSize = 30;
    public int autosaveIntervalSeconds = 120;

    //sensitivity
    public double gamma = 0;
    public int sense = 0;

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