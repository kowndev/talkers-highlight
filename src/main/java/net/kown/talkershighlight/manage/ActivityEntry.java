package net.kown.talkershighlight.manage;
import java.util.UUID;

    // Holds the runtime state for a single player's tracer line.
    // Simple Voice Chat fires PlayerSoundEvent for every ~20 ms audio

public class ActivityEntry {

    // applied once per game tick.
    // 0.72 -> ~52% left after 2 ticks (~100ms), ~7% left after 8 ticks (~400ms).
    // Fast enough that the natural gaps between syllables visibly dip before the next peak arrives
    // producing a flicker instead of a smooth fade.
    private static final float DECAY_PER_TICK = 0.72f;

    private final UUID playerUUID;

    // Wall-clock ms timestamp of the last received audio event.
    private long lastSeen;

     // Peak-hold amplitude in [0.0, 1.0].
     // 0 -> green, 1 -> red (per config colour gradient).
    private float smoothedAmplitude;

    public ActivityEntry(UUID playerUUID, float initialAmplitude) {
        this.playerUUID        = playerUUID;
        this.lastSeen          = System.currentTimeMillis();
        this.smoothedAmplitude = clamp01(initialAmplitude);
    }

    public void onSoundReceived(float rawAmplitude) {
        lastSeen = System.currentTimeMillis();
        float raw = clamp01(rawAmplitude);
        if (raw > smoothedAmplitude) {
            smoothedAmplitude = raw;
        }
    }

    // Called once per game tick by ActivityManager
    // Applies the per-tick decay so a captured peak falls away between
    public void tickDecay() {
        smoothedAmplitude = clamp01(smoothedAmplitude * DECAY_PER_TICK);
    }
    public boolean isExpired(long persistMs) {
        return (System.currentTimeMillis() - lastSeen) > persistMs;
    }
    public UUID  getPlayerUUID()       { return playerUUID;       }
    public long  getLastSeen()         { return lastSeen;          }
    public float getSmoothedAmplitude(){ return smoothedAmplitude; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}