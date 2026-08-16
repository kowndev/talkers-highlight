package net.kown.talkershighlight.manage;

import net.kown.talkershighlight.config.Config;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.kown.talkershighlight.logger.LoggerManager.addEntry;

public final class ActivityManager {

    public static final ActivityManager INSTANCE = new ActivityManager();

    private static final float MAX_AMPLITUDE = 0.375f;

    // UUID → active tracer
    private static final ConcurrentHashMap<UUID, ActivityEntry> tracers = new ConcurrentHashMap<>();

    private ActivityManager() {}

    // Called from SVC audio thread
    public static void onPlayerTalking(UUID playerUUID, float amplitude) {
        Config cfg = Config.INSTANCE;
        if (amplitude >= cfg.minVolume){
            addEntry(playerUUID, amplitude);
        }else return;

        float sensitivity = cfg.sense / 10f; // 0.0 at floor<=1, 1.0 at floor=10
        float noise_floor = MAX_AMPLITUDE * (1f - sensitivity);
        float range = Math.max(0.0001f, MAX_AMPLITUDE - noise_floor);

        float normalized = (amplitude - noise_floor) / range;
        float boostedAmplitude = Math.max(0f, Math.min(1f, normalized)); // no curve — linear

        // feed boostedAmplitude (0 = green, 1 = red) into your color interpolation

        if (!cfg.TracerEnabled && !cfg.HighlightEnabled) return;

        tracers.compute(playerUUID, (uuid, existing) -> {
            if (existing == null) {
                return new ActivityEntry(uuid, boostedAmplitude);
            }
            existing.onSoundReceived(boostedAmplitude);
            return existing;
        });
    }

    // Called from main game thread (ClientTickEvents)
    // Performs per-tick housekeeping:
    public void tick(Set<UUID> onlineUUIDs) {
        int persistMs = Config.INSTANCE.PersistanceMs;

        tracers.entrySet().removeIf(e -> {
            UUID uuid = e.getKey();
            ActivityEntry entry = e.getValue();

            // Player left the world → remove immediately.
            if (!onlineUUIDs.contains(uuid)) return true;

            // Persist timer elapsed → remove.
            if (entry.isExpired(persistMs)) return true;

            // Still active → decay amplitude for this tick.
            entry.tickDecay();
            return false;
        });
    }

    // ── Rendering read-path ───────────────────────────────────────────────────

    // Snapshot of currently active tracers; safe to iterate on the render thread.
    public Collection<ActivityEntry> getActiveEntries() {
        return Collections.unmodifiableCollection(tracers.values());
    }

    // Clears all tracers (e.g., when leaving a world).
    public void clear() {
        tracers.clear();
    }

}