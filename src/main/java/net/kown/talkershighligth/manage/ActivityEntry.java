package net.kown.talkershighligth.manage;
import java.util.UUID;

/**
 * Holds the runtime state for a single player's tracer line.
 *
 * <h3>Amplitude model</h3>
 * Simple Voice Chat fires {@code PlayerSoundEvent} for every ~20 ms audio
 * chunk it receives.  We track two things:
 * <ol>
 *   <li><b>smoothedAmplitude</b> – interpolated towards the raw value on each
 *       event, decayed each game-tick.  Used for colour and thickness.</li>
 *   <li><b>lastSeen</b> – wall-clock time of the most recent event, used for
 *       the persist-timer.</li>
 * </ol>
 */
public class ActivityEntry {

    // How quickly the displayed amplitude falls between events (per tick).
    // 0.92 → drops to ~45 % after 10 ticks (0.5 s at 20 TPS).
    private static final float DECAY_PER_TICK = 0.92f;

    // Lerp factor when blending a new raw sample into the smoothed value.
    private static final float SMOOTH_UP   = 0.55f;   // fast rise
    private static final float SMOOTH_DOWN = 0.25f;   // slower fall between events

    private final UUID playerUUID;

    /** Wall-clock ms timestamp of the last received audio event. */
    private long lastSeen;

    /**
     * Smoothed amplitude in [0.0, 1.0].
     * 0 → green, 1 → red (per config colour gradient).
     */
    private float smoothedAmplitude;

    // ── Construction ──────────────────────────────────────────────────────────

    public ActivityEntry(UUID playerUUID, float initialAmplitude) {
        this.playerUUID        = playerUUID;
        this.lastSeen          = System.currentTimeMillis();
        this.smoothedAmplitude = clamp01(initialAmplitude);
    }

    // ── Update API ────────────────────────────────────────────────────────────

    /**
     * Called every time an audio packet is received from this player.
     *
     * @param rawAmplitude  Normalised amplitude [0.0 – 1.0].
     *                      Pass {@code 1.0f} if the SVC API does not expose
     *                      per-packet amplitude; the decay curve will then
     *                      produce a natural fade once talking stops.
     */
    public void onSoundReceived(float rawAmplitude) {
        lastSeen = System.currentTimeMillis();
        float raw = clamp01(rawAmplitude);
        // Rise quickly when getting louder, fall slowly otherwise.
        float alpha = (raw > smoothedAmplitude) ? SMOOTH_UP : SMOOTH_DOWN;
        smoothedAmplitude = lerp(smoothedAmplitude, raw, alpha);
    }

    /**
     * Called once per game tick by {@link ActivityManager#tick}.
     * Applies the per-tick amplitude decay so the line fades after the player
     * stops talking.
     */
    public void tickDecay() {
        smoothedAmplitude = clamp01(smoothedAmplitude * DECAY_PER_TICK);
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /** @return {@code true} when the persist timer has elapsed. */
    public boolean isExpired(long persistMs) {
        return (System.currentTimeMillis() - lastSeen) > persistMs;
    }

    public UUID  getPlayerUUID()       { return playerUUID;       }
    public long  getLastSeen()         { return lastSeen;          }
    public float getSmoothedAmplitude(){ return smoothedAmplitude; }

    // ── Internal helpers ──────────────────────────────────────────────────────
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}