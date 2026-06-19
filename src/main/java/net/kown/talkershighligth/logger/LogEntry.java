package net.kown.talkershighligth.logger;

import java.util.UUID;

/**
 * Immutable record representing a single logged entry.
 *
 * @param time  Epoch millis timestamp, generated at the moment of receipt.
 * @param uuid  Player UUID; used as the unique key for de-duplication.
 * @param value The numeric value collected from the other code segment.
 */
public record LogEntry(long time, UUID uuid, float value) {}