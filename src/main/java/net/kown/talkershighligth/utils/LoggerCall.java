package net.kown.talkershighligth.utils;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.kown.talkershighligth.logger.LogEntry;
import net.kown.talkershighligth.logger.LoggerManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class LoggerCall {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private LoggerCall() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("THLogger")
                .then(ClientCommandManager.literal("loud")
                        .executes(ctx -> {
                            show(ctx, LoggerManager.getHighestSortedByValueDesc(), "Loudest");
                            return 1;
                        }))
                .then(ClientCommandManager.literal("latest")
                        .executes(ctx -> {
                            show(ctx, LoggerManager.getLatestSortedByTimeDesc(), "Most Recent");
                            return 1;
                        }))
        );
    }

    private static void show(CommandContext<FabricClientCommandSource> ctx,
                             List<LogEntry> list, String label) {
        FabricClientCommandSource source = ctx.getSource();

        if (list.isEmpty()) {
            source.sendFeedback(Text.literal("[THLogger] No data collected yet.")
                    .formatted(Formatting.GRAY));
            return;
        }

        source.sendFeedback(Text.literal("=== THLogger - " + label + " ===")
                .formatted(Formatting.GOLD));

        int rank = 1;
        for (LogEntry e : list) {
            String time = Instant.ofEpochMilli(e.time())
                    .atZone(ZoneId.systemDefault()).format(TIME_FORMAT);
            String username = LoggerNameCache.getDisplayName(e.uuid());
            source.sendFeedback(Text.literal(
                    String.format("%d. [%s] %s - %s", rank++, time, username, e.value())));
        }
    }
}