package net.kown.talkershighlight.utils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

    // Caches UUID -> username lookups so NameUUIDSearch's blocking Mojang API
    // runs once per player, on a background thread
    // Usage pattern: call getDisplayName() => known = cached name or unknown = UUID
public final class LoggerNameCache {

    private static final Map<UUID, String> cache = new ConcurrentHashMap<>();
    private static final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();

    private static final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "THLogger-NameLookup");
        t.setDaemon(true); // never blocks JVM exit
        return t;
    });

    private LoggerNameCache() {}

    public static String getDisplayName(UUID uuid) {
        String cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        resolveAsync(uuid);
        return uuid.toString();
    }

    // start background UUID lookup if not been resolved
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
