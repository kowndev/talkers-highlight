package net.kown.talkershighligth.tracer;

import net.kown.talkershighligth.config.Config;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of active tracer entries.
 *
 * <ul>
 *   <li>{@link #onPlayerTalking} is called from the audio thread (via SVC).</li>
 *   <li>{@link #tick} is called from the main game thread each client tick.</li>
 * </ul>
 * {@link ConcurrentHashMap} makes cross-thread access safe without explicit locking.
 */
public final class TracerManager {

    public static final TracerManager INSTANCE = new TracerManager();

    /** UUID → active tracer.  May be written from the audio thread. */
    private static final ConcurrentHashMap<UUID, TracerEntry> tracers = new ConcurrentHashMap<>();

    private TracerManager() {}

    // ── Called from SVC audio thread ──────────────────────────────────────────

    /**
     * Registers or refreshes a tracer for {@code playerUUID}.
     *
     * @param playerUUID  UUID of the talking player.
     * @param amplitude   Normalised amplitude [0.0 – 1.0].
     *                    Use {@code 1.0f} if SVC does not expose raw amplitude.
     */
    public static void onPlayerTalking(UUID playerUUID, float amplitude) {
        Config cfg = Config.INSTANCE;
        if (!cfg.TracerEnabled)             return;
        if (amplitude < cfg.minVolume) return;

        tracers.compute(playerUUID, (uuid, existing) -> {
            if (existing == null) {
                return new TracerEntry(uuid, amplitude);
            }
            existing.onSoundReceived(amplitude);
            return existing;
        });
    }

    // ── Called from main game thread (ClientTickEvents) ───────────────────────

    /**
     * Performs per-tick housekeeping:
     * <ol>
     *   <li>Removes entries whose player has left the world.</li>
     *   <li>Removes entries whose persist-timer has elapsed.</li>
     *   <li>Decays the smoothed amplitude of remaining entries.</li>
     * </ol>
     *
     * @param onlineUUIDs  Set of UUIDs currently loaded in the client world.
     */
    public void tick(Set<UUID> onlineUUIDs) {
        int persistMs = Config.INSTANCE.tracerPersistMs;

        tracers.entrySet().removeIf(e -> {
            UUID uuid = e.getKey();
            TracerEntry entry = e.getValue();

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

    /** Snapshot of currently active tracers; safe to iterate on the render thread. */
    public Collection<TracerEntry> getActiveTracers() {
        return Collections.unmodifiableCollection(tracers.values());
    }

    /** Clears all tracers (e.g., when leaving a world). */
    public void clear() {
        tracers.clear();
    }

}