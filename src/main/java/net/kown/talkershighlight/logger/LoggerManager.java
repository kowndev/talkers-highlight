package net.kown.talkershighlight.logger;

import net.fabricmc.loader.api.FabricLoader;
import net.kown.talkershighlight.config.Config;
import net.kown.talkershighlight.utils.LoggerNameCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class LoggerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("THLogger");
    private static final List<LogEntry> recentTable = new ArrayList<>();
    private static final List<LogEntry> highestTable = new ArrayList<>();

    private static final DateTimeFormatter LINE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILENAME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"); // no colons - filesystem safe

    static Config cfg = Config.INSTANCE;

    private LoggerManager() {}

    public static synchronized void addEntry(UUID uuid, float value) {
        LogEntry entry = new LogEntry(System.currentTimeMillis(), uuid, value);
        LoggerNameCache.resolveAsync(uuid);
        updateRecentTable(entry);
        updateHighestTable(entry);
    }

    private static void updateRecentTable(LogEntry entry) {
        recentTable.removeIf(e -> e.uuid().equals(entry.uuid()));
        recentTable.add(entry);

        int maxSize = cfg.listSize;
        recentTable.sort(Comparator.comparingLong(LogEntry::time).reversed());
        while (recentTable.size() > maxSize) {
            recentTable.removeLast(); // drop oldest
        }
    }

    private static void updateHighestTable(LogEntry entry) {
        int maxSize = cfg.listSize;

        Optional<LogEntry> existing = highestTable.stream()
                .filter(e -> e.uuid().equals(entry.uuid()))
                .findFirst();

        if (existing.isPresent()) {
            if (entry.value() >= existing.get().value()) {
                highestTable.remove(existing.get());
                highestTable.add(entry);
            }
            return;
        }

        if (highestTable.size() < maxSize) {
            highestTable.add(entry);
            return;
        }

        LogEntry lowest = highestTable.stream()
                .min(Comparator.comparingDouble(LogEntry::value)
                        .thenComparing(LogEntry::time))
                .orElse(null);
        if (lowest != null && entry.value() >= lowest.value()) {
            highestTable.remove(lowest);
            highestTable.add(entry);
        }
    }

    // Backing data for /THLogger latest
    public static synchronized List<LogEntry> getLatestSortedByTimeDesc() {
        List<LogEntry> copy = new ArrayList<>(recentTable);
        copy.sort(Comparator.comparingLong(LogEntry::time).reversed());
        return copy;
    }

    // Backing data for /THLogger loud
    public static synchronized List<LogEntry> getHighestSortedByValueDesc() {
        List<LogEntry> copy = new ArrayList<>(highestTable);
        copy.sort(Comparator.comparingDouble(LogEntry::value).reversed());
        return copy;
    }

    public static synchronized void clear() {
        recentTable.clear();
        highestTable.clear();
    }

    public static synchronized void writeAutosave() {
        Path logDir = FabricLoader.getInstance().getGameDir().resolve("thlogger");
        writeMergedTo(logDir.resolve("thlogger_autosave.txt"));
    }

     // Final flush for a session (world disconnect, or a backup trigger).
     // Timestamped filename so it never overwrites a previous session's log.
    public static synchronized void writeFinalLog() {
        Path logDir = FabricLoader.getInstance().getGameDir().resolve("thlogger");
        String stamp = Instant.now().atZone(ZoneId.systemDefault()).format(FILENAME_FORMAT);
        writeMergedTo(logDir.resolve("thlogger_log_" + stamp + ".txt"));
    }

    // merge recent and latest table to txt file
    private static void writeMergedTo(Path logFile) {
        List<LogEntry> merged = new ArrayList<>(recentTable.size() + highestTable.size());
        merged.addAll(recentTable);
        merged.addAll(highestTable);
        if (merged.isEmpty()) {
            LOGGER.info("[THLogger] Skipping write to {} - no entries collected yet.", logFile.toAbsolutePath());
            return;
        }
        merged.sort(Comparator.comparingLong(LogEntry::time).reversed());

        try {
            Files.createDirectories(logFile.getParent());

            List<String> lines = new ArrayList<>();
            lines.add("THLogger log - written " +
                    Instant.now().atZone(ZoneId.systemDefault()).format(LINE_FORMAT));
            lines.add("Time | Username | UUID | Value");
            for (LogEntry e : merged) {
                String time = Instant.ofEpochMilli(e.time())
                        .atZone(ZoneId.systemDefault()).format(LINE_FORMAT);
                String username = LoggerNameCache.getDisplayName(e.uuid());
                lines.add(String.format("%s | %s | %s | %s",
                        time, username, e.uuid(), e.value()));
            }
            Files.write(logFile, lines, StandardCharsets.UTF_8);
            LOGGER.info("[THLogger] Wrote {} entries to {}", merged.size(), logFile.toAbsolutePath());
        } catch (IOException ex) {
            LOGGER.error("[THLogger] Failed to write log to {}", logFile.toAbsolutePath(), ex);
        }
    }
}