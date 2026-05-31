package net.kown.talkershighligth.bridge;

import net.kown.talkershighligth.tracer.TracerManager;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatClientPlugin;
import de.maxhenkel.voicechat.api.events.PlayerSoundEvent;

import java.util.UUID;

/**
 * Simple Voice Chat client plugin.
 *
 * <p>Registered via the Java ServiceLoader file at:<br>
 * {@code META-INF/services/de.maxhenkel.voicechat.api.VoicechatPlugin}
 *
 * <h3>Amplitude note</h3>
 * The SVC API fires {@link PlayerSoundEvent} for every ~20 ms audio chunk
 * received from another player.  The API does <em>not</em> currently expose
 * per-packet RMS amplitude in its public interface, so we pass {@code 1.0f}
 * here and rely on {@link com.voicechattracer.tracer.TracerEntry}'s built-in
 * exponential-decay smoother to create the colour-fade effect.<br>
 * <br>
 * <b>If a future SVC API version adds {@code event.getAmplitude()}</b>,
 * replace the {@code 1.0f} literal in {@link #onPlayerSound} with that call
 * for fully dynamic colour gradients.
 *
 * <h3>UUID extraction</h3>
 * Adjust {@link #onPlayerSound} if your exact SVC build uses a different
 * method chain — see the inline comment.
 */
public class VoiceChatPlugin implements VoicechatClientPlugin {

    @Override
    public void initialize(VoicechatClientApi api) {
        // Subscribe to PlayerSoundEvent – fired on the audio thread for each
        // incoming voice packet from another player in the same voice chat group.
        api.getEventBus().subscribe(this, PlayerSoundEvent.class, this::onPlayerSound);
    }

    private void onPlayerSound(PlayerSoundEvent event) {
        UUID playerUUID = resolveUUID(event);
        if (playerUUID == null) return;

        // ─────────────────────────────────────────────────────────────────────
        // Amplitude placeholder: 1.0f = "definitely talking".
        // The smoothed decay in TracerEntry will create a natural fade once
        // events stop arriving, giving the impression of a pulsing signal.
        //
        // To use real amplitude (if your SVC build supports it):
        //   float amplitude = event.getAmplitude();          // hypothetical
        //   TracerManager.INSTANCE.onPlayerTalking(playerUUID, amplitude);
        // ─────────────────────────────────────────────────────────────────────
        TracerManager.INSTANCE.onPlayerTalking(playerUUID, 1.0f);
    }

    /**
     * Extracts the speaker's UUID from the event.
     *
     * <p>SVC API method naming has varied across releases.  The chains below
     * cover the most common patterns – uncomment the one that compiles for
     * your specific {@code voicechat-api} version.
     */
    private UUID resolveUUID(PlayerSoundEvent event) {
        try {
            // ── Pattern A – SVC 2.5.x (most common) ──────────────────────────
            return event.getSenderUuid();

            // ── Pattern B – some older 2.4.x builds ──────────────────────────
            // return event.getConnection().getPlayer().getId();

            // ── Pattern C – alternative naming ───────────────────────────────
            // return event.getPlayer().getUUID();
        } catch (Exception ignored) {
            // API mismatch — try the next pattern.
            return null;
        }
    }
}