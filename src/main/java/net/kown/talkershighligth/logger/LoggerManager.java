package net.kown.talkershighligth.logger;

import net.fabricmc.loader.api.FabricLoader;
import net.kown.talkershighligth.config.Config;
import net.kown.talkershighligth.utils.LoggerNameCache;
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

/**
 * Holds two separate rolling tables so "most recent" and "personal best"
 * don't fight over the same slot:
 *
 *  - recentTable:  one entry per player, always their latest submission.
 *                  Capped at listSize; oldest (by time) evicted first.
 *  - highestTable: one entry per player, only replaced if the new value
 *                  beats or ties their existing entry IN THIS TABLE (ties
 *                  go to the newer submission). Capped at listSize; lowest
 *                  value evicted first when full, oldest first on a tie.
 *
 * Entries store only time/UUID/value - usernames are resolved lazily via
 * THLoggerNameCache only when something is actually displayed or logged.
 *
 * Two kinds of file writes:
 *  - writeAutosave(): periodic safety-net snapshot, overwrites one stable
 *    filename every call. Not an archive - just "best known state."
 *  - writeFinalLog(): called when a session ends (world disconnect, or a
 *    backup trigger like client stopping / shutdown hook). Filename is
 *    timestamped so multiple disconnects in the same client run each get
 *    their own file instead of clobbering each other.
 *
 * All access is synchronized since data may arrive on a network/event
 * thread while commands and the autosave/exit writer read it elsewhere.
 */
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

    /**
     * Call this from your other code segment whenever new data arrives.
     * Time is stamped at the moment of receipt. No username lookup happens
     * here - that's deferred until display/log time.
     */
    public static synchronized void addEntry(UUID uuid, float value) {
        LogEntry entry = new LogEntry(System.currentTimeMillis(), uuid, value);
        // Kick off name resolution the moment data arrives, not just when it's
        // displayed. highestTable entries in particular can sit untouched for a
        // long time after the player drops out of recentTable, so waiting until
        // display time means the very first /THLogger loud for that player is
        // guaranteed to show a raw UUID with no chance to self-correct.
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

    /** Backing data for /THLogger latest. */
    public static synchronized List<LogEntry> getLatestSortedByTimeDesc() {
        List<LogEntry> copy = new ArrayList<>(recentTable);
        copy.sort(Comparator.comparingLong(LogEntry::time).reversed());
        return copy;
    }

    /** Backing data for /THLogger loud. */
    public static synchronized List<LogEntry> getHighestSortedByValueDesc() {
        List<LogEntry> copy = new ArrayList<>(highestTable);
        copy.sort(Comparator.comparingDouble(LogEntry::value).reversed());
        return copy;
    }

    public static synchronized void clear() {
        recentTable.clear();
        highestTable.clear();
    }

    /**
     * Periodic safety-net snapshot. Always overwrites the same filename -
     * intentionally not timestamped, since it's meant to reflect "current
     * state" in case of an abrupt termination, not a historical record.
     */
    public static synchronized void writeAutosave() {
        Path logDir = FabricLoader.getInstance().getGameDir().resolve("thlogger");
        writeMergedTo(logDir.resolve("thlogger_autosave.txt"));
    }

    /**
     * Final flush for a session (world disconnect, or a backup trigger).
     * Timestamped filename so it never overwrites a previous session's log.
     */
    public static synchronized void writeFinalLog() {
        Path logDir = FabricLoader.getInstance().getGameDir().resolve("thlogger");
        String stamp = Instant.now().atZone(ZoneId.systemDefault()).format(FILENAME_FORMAT);
        writeMergedTo(logDir.resolve("thlogger_log_" + stamp + ".txt"));
    }

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