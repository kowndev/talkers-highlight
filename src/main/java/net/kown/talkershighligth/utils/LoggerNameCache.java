package net.kown.talkershighligth.utils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Caches UUID -> username lookups so NameUUIDSearch's blocking Mojang API
 * call only ever runs once per player, on a background thread, instead of
 * on the main thread or repeatedly for the same UUID.
 *
 * Usage pattern: call getDisplayName() wherever you're about to show or log
 * a name. It never blocks - it returns the cached name if known, otherwise
 * the raw UUID string as an immediate fallback, while quietly kicking off a
 * background resolution so future calls return the real name once it's in.
 */
public final class LoggerNameCache {

    private static final Map<UUID, String> cache = new ConcurrentHashMap<>();
    private static final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();

    private static final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "THLogger-NameLookup");
        t.setDaemon(true); // never blocks JVM exit
        return t;
    });

    private LoggerNameCache() {}

    /**
     * Best-known display name for a UUID, resolved or not. Never blocks.
     * Falls back to the UUID's string form until/unless a lookup resolves.
     */
    public static String getDisplayName(UUID uuid) {
        String cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        resolveAsync(uuid);
        return uuid.toString();
    }

    /**
     * Kicks off a background lookup if this UUID hasn't been resolved (or
     * isn't already being resolved). Safe to call repeatedly/concurrently -
     * only ever schedules one in-flight lookup per UUID.
     */
    public static void resolveAsync(UUID uuid) {
        if (cache.containsKey(uuid)) return;
        if (!inProgress.add(uuid)) return; // already in flight

        executor.submit(() -> {
            try {
                String name = NameUUIDSearch.id(uuid); // blocking call - fine, we're off-thread
                if (name != null && !name.isBlank()) {
                    cache.put(uuid, name);
                }
                // else: leave uncached on failure, so a future call retries
                // instead of getting permanently stuck on the UUID fallback.
            } finally {
                inProgress.remove(uuid);
            }
        });
    }

    public static void clear() {
        cache.clear();
        inProgress.clear();
    }
}
