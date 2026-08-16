package net.kown.talkershighlight.logger;

import java.util.UUID;

public record LogEntry(long time, UUID uuid, float value) {}