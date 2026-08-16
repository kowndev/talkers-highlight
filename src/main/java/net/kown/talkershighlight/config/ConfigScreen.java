package net.kown.talkershighlight.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.awt.Color;

public final class ConfigScreen {

    private ConfigScreen() {}

    public static Screen createScreen(Screen parent) {
        Config cfg = Config.INSTANCE;

        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("VoiceChat Tracer — Settings"))

                // ── Tab 1: General ────────────────────────────────────────────
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("General"))
                        .tooltip(Text.literal("Master switch and identification."))

                        .option(Option.<Boolean>createBuilder()
                                .name(Text.literal("Enable Tracer"))
                                .description(OptionDescription.of(
                                        Text.literal("Toggle the entire tracer overlay on or off.\n"
                                                + "You can also press the Toggle keybind (default: J) in-game.")))
                                .binding(true, () -> cfg.TracerEnabled, v -> cfg.TracerEnabled = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .option(Option.<Boolean>createBuilder()
                                .name(Text.literal("Enable Highlighter"))
                                .description(OptionDescription.of(
                                        Text.literal("Toggle the entire Highlight overlay on or off.")))
                                .binding(false, () -> cfg.HighlightEnabled, v -> cfg.HighlightEnabled = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Text.literal("Stored Entries"))
                                .description(OptionDescription.of(
                                        Text.literal("Maximum number of entries kept in memory and written to the log on exit.")))
                                .binding(10, () -> cfg.listSize, v -> cfg.listSize = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 100).step(1))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Text.literal("Autosave Interval (seconds)"))
                                .description(OptionDescription.of(
                                        Text.literal("How often the log file is refreshed during play, in case the client is killed abruptly. Takes effect after a restart.")))
                                .binding(30, () -> cfg.autosaveIntervalSeconds, v -> cfg.autosaveIntervalSeconds = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(5, 300).step(5))
                                .build())

                        .build())

                // ── Tab 2: Activation ─────────────────────────────────────────
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Activation"))
                        .tooltip(Text.literal("When tracers appear and how long they linger."))

                        .option(Option.<Float>createBuilder()
                                .name(Text.literal("Minimum Volume Threshold"))
                                .description(OptionDescription.of(Text.literal(
                                        "Normalised amplitude [0.00 – 1.00] that must be exceeded before\n"
                                                + "a tracer is drawn.  Raise this to suppress open-mic noise.")))
                                .binding(0.05f, () -> cfg.minVolume, v -> cfg.minVolume = v)
                                .controller(opt -> FloatSliderControllerBuilder.create(opt)
                                        .range(0.00f, 1.00f).step(0.01f)
                                        .formatValue(v -> Text.literal(String.format("%.2f", v))))
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Text.literal("Tracer Persist Time"))
                                .description(OptionDescription.of(Text.literal(
                                        "How long (milliseconds) a tracer remains visible after the last\n"
                                                + "audio packet is received.  Higher values look smoother for\n"
                                                + "voice-activation (push-to-talk gaps).")))
                                .binding(2000, () -> cfg.PersistanceMs, v -> cfg.PersistanceMs = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(100, 10_000).step(100)
                                        .formatValue(v -> Text.literal(v + " ms")))
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Text.literal("Tracer Sensitivity"))
                                .description(OptionDescription.of(
                                        Text.literal(
                                                "Controls how strongly the tracer reacts when someone is actually talking.\n"
                                                        + "Higher = talking makes the line jump to full thickness clearly, easier to spot at a glance.\n"
                                                        + "Lower = a more subtle, natural-looking response.\n"
                                        )))
                                .binding(5, () -> cfg.sense, v -> cfg.sense = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(0, 10).step(1))
                                .build())

                        .build())

                // ── Tab 3: Appearance ─────────────────────────────────────────
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Appearance"))
                        .tooltip(Text.literal("Line thickness and colour gradient."))

                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Line Thickness"))
                                .description(OptionDescription.of(Text.literal(
                                        "Thickness of the tracer line in pixels.\n"
                                                + "Min is used at low volume; Max is used at peak volume.\n"
                                                + "Note: Many GPU drivers cap hardware line width to 1 px.")))

                                .option(Option.<Float>createBuilder()
                                        .name(Text.literal("Min Thickness"))
                                        .binding(1.0f, () -> cfg.minLineWidth, v -> cfg.minLineWidth = v)
                                        .controller(opt -> FloatSliderControllerBuilder.create(opt)
                                                .range(0.5f, 20.0f)
                                                .step(0.5f)
                                                .formatValue(v -> Text.literal(String.format("%.1f px", v))))
                                        .build())

                                .option(Option.<Float>createBuilder()
                                        .name(Text.literal("Max Thickness"))
                                        .binding(3.5f, () -> cfg.maxLineWidth, v -> cfg.maxLineWidth = v)
                                        .controller(opt -> FloatSliderControllerBuilder.create(opt)
                                                .range(0.5f, 20.0f)
                                                .step(0.5f)
                                                .formatValue(v -> Text.literal(String.format("%.1f px", v))))
                                        .build())

                                .build())

                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Colour Gradient"))
                                .description(OptionDescription.of(Text.literal(
                                        "The tracer colour lerps from Low → High based on the\n"
                                                + "player's smoothed amplitude.  Alpha is respected.")))

                                .option(Option.<Color>createBuilder()
                                        .name(Text.literal("Low Volume Colour"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Colour shown when the player is just barely above the\n"
                                                        + "minimum volume threshold (quiet speech).\n"
                                                        + "Default: semi-transparent green.")))
                                        .binding(
                                                new Color(0, 230, 0, 220),
                                                cfg::getLowVolumeColor,
                                                v -> cfg.lowVolumeColor = v.getRGB())
                                        .controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(true))
                                        .build())

                                .option(Option.<Color>createBuilder()
                                        .name(Text.literal("High Volume Colour"))
                                        .description(OptionDescription.of(Text.literal(
                                                "Colour shown when the player is speaking loudly / at peak\n"
                                                        + "amplitude.\n"
                                                        + "Default: semi-transparent red.")))
                                        .binding(
                                                new Color(230, 0, 0, 220),
                                                cfg::getHighVolumeColor,
                                                v -> cfg.highVolumeColor = v.getRGB())
                                        .controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(true))
                                        .build())

                                .build())

                        .build())

                // Persist to disk when the screen is closed.
                .save(Config::save)
                .build()
                .generateScreen(parent);
    }
}