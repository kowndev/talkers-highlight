package net.kown.talkershighlight.bridge;

import net.kown.talkershighlight.TalkersHighlightClient;
import net.kown.talkershighlight.manage.ActivityManager;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;

import java.util.UUID;

public class VoiceChatPluginV1 implements VoicechatPlugin{
    @Override
    public String getPluginId() {
        return "vc-addon-" + TalkersHighlightClient.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatPlugin.super.initialize(api);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, this::onPlayerSound);
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, this::onPlayerSound);
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, this::onPlayerSound);
        //registration.registerEvent(ClientSoundEvent.class, this::onPlayerSound);
        VoicechatPlugin.super.registerEvents(registration);
    }

    private void onPlayerSound(ClientReceiveSoundEvent event) {
        UUID playerUUID = resolveUUID(event);
        if (playerUUID == null) return;
        ActivityManager.INSTANCE.onPlayerTalking(playerUUID,
                calculateAudioLevel(event.getRawAudio()));

        // Amplitude placeholder: 1.0f = "definitely talking".


    }

    // Extracts the speaker's UUID from the event.
    private UUID resolveUUID(ClientReceiveSoundEvent event) {
        try {

            // ── Pattern A – SVC 2.5.x (most common) ──────────────────────────
            return event.getId();

            // ── Pattern B – some older 2.4.x builds ──────────────────────────
            //return event.getConnection().getPlayer().getId();

            // ── Pattern C – alternative naming ───────────────────────────────
            // return event.getPlayer().getUUID();

        } catch (Exception ignored) {
            // API mismatch — try the next pattern.
            return null;
        }
    }

//    public static float calculateAudioLevel(short[] samples) {
//        //float MIN_DB = -127F;
//        float MIN_DB = 0F;
//
//        if (samples == null || samples.length == 0) {
//            return MIN_DB;
//        }
//
//        float sum = 0F;
//        for (short sample : samples) {
//            float normalized = sample / (float) Short.MAX_VALUE;
//            sum += normalized * normalized;
//        }
//
//        double rms = Math.sqrt(sum / samples.length);
//        if (rms <= 0F) {return MIN_DB;}
//
//        double db = (20.0 * Math.log10(rms));
//        return Math.max((float)db, MIN_DB);
//    }

    public static float calculateAudioLevel(short[] samples) {
        if (samples == null || samples.length == 0) {
            return 0f;
        }

        double sum = 0.0;

        for (short sample : samples) {
            sum += (double) sample * sample;
        }

        double rms = Math.sqrt(sum / samples.length);

        // Normalize to 0..1
        double normalized = rms / 32768.0;

        // Apply perceptual curve
        normalized = Math.sqrt(normalized);

        //TalkersHighlightClient.LOGGER.warn("[THD] RMS: {}", rms);
        return (float) Math.max(0.0, Math.min(1.0, normalized));
    }
}
