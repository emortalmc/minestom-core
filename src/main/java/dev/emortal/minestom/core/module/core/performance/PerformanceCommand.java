package dev.emortal.minestom.core.module.core.performance;

import dev.emortal.minestom.core.utils.DurationFormatter;
import dev.emortal.minestom.core.utils.ProgressBar;
import com.sun.management.GcInfo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import org.jetbrains.annotations.NotNull;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PerformanceCommand extends Command {
    private static final long SECONDS_IN_NANO = 1_000_000_000L;
    private static final int TPS = MinecraftServer.TICK_PER_SECOND;
    private static final BigDecimal TPS_BASE = new BigDecimal(SECONDS_IN_NANO).multiply(new BigDecimal(TPS));

    private static final Component VALUE_SEPARATOR = Component.text(", ");
    private static final Set<Pattern> EXCLUDED_MEMORY_SPACES = Stream.of("Metaspace", "Compressed Class Space", "^CodeHeap")
            .map(Pattern::compile).collect(Collectors.toUnmodifiableSet());

    private final TpsRollingAverage tps5s = new TpsRollingAverage(5);
    private final TpsRollingAverage tps15s = new TpsRollingAverage(15);
    private final TpsRollingAverage tps1m = new TpsRollingAverage(60);
    private final TpsRollingAverage tps5m = new TpsRollingAverage(60 * 5);
    private final TpsRollingAverage tps15m = new TpsRollingAverage(60 * 15);
    private final TpsRollingAverage[] tpsAverages = {tps5s, tps15s, tps1m, tps5m, tps15m};

    private final RollingAverage mspt5s = new RollingAverage(TPS * 5);
    private final RollingAverage mspt15s = new RollingAverage(TPS * 15);
    private final RollingAverage mspt1m = new RollingAverage(TPS * 60);
    private final RollingAverage mspt5m = new RollingAverage(TPS * 60 * 5);
    private final RollingAverage mspt15m = new RollingAverage(TPS * 60 * 15);
    private final RollingAverage[] msptAverages = {mspt5s, mspt15s, mspt1m, mspt5m, mspt15m};

    private long lastTickTime;

    public PerformanceCommand(EventNode<Event> eventNode) {
        super("performance");

        eventNode.addListener(ServerTickMonitorEvent.class, event -> onTick(event.getTickMonitor().getTickTime()));

        this.addSyntax(this::onExecute);
    }

    private void onTick(double tickTime) {
        if (lastTickTime == 0) {
            lastTickTime = System.nanoTime();
            return;
        }

        final long now = System.nanoTime();
        final long difference = now - lastTickTime;
        if (difference <= SECONDS_IN_NANO) return;

        final BigDecimal currentTps = TPS_BASE.divide(new BigDecimal(difference), 30, RoundingMode.HALF_UP);
        final BigDecimal total = currentTps.multiply(new BigDecimal(difference));

        for (final TpsRollingAverage average : tpsAverages) {
            average.addSample(currentTps, difference, total);
        }
        lastTickTime = now;

        final BigDecimal duration = new BigDecimal(tickTime);
        for (final RollingAverage average : msptAverages) {
            average.addSample(duration);
        }
    }

    private void onExecute(@NotNull CommandSender sender, @NotNull CommandContext context) {
        this.getGcInfo();

        long totalMem = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        long freeMem = Runtime.getRuntime().freeMemory() / 1024 / 1024;
        long ramUsage = totalMem - freeMem;
        float ramPercent = (float) ramUsage / (float) totalMem;

        sender.sendMessage(
                Component.text()
                        .append(Component.newline())
                        .append(Component.text("RAM Usage: ", NamedTextColor.GRAY))
                        .append(ProgressBar.create(ramPercent, 30, "┃", NamedTextColor.GREEN, TextColor.color(0, 123, 0)))
                        .append(Component.text(String.format(" %sMB / %sMB\n", ramUsage, totalMem), NamedTextColor.GRAY))

                        .append(Component.newline())
                        .append(this.createGcComponent())
                        .append(Component.newline())

                        .append(Component.text("\nTPS (5s, 15s, 1m, 5m, 15m): ", NamedTextColor.GRAY))
                        .append(getTpsInfo(tpsAverages))
                        .append(Component.newline())
                        .append(Component.text("MSPT (5s, 15s, 1m, 5m, 15m): ", NamedTextColor.GRAY))
                        .append(getMsptInfo(msptAverages))
        );
    }

    private Component getTpsInfo(TpsRollingAverage[] averages) {
        final TextComponent.Builder builder = Component.text();

        for (int i = 0; i < averages.length; i++) {
            final double average = averages[i].average();
            final TextColor color = calculateTpsColor(average);
            builder.append(Component.text(String.format("%.2f", average), color));
            if (i < averages.length - 1) builder.append(VALUE_SEPARATOR);
        }

        return builder.build();
    }

    private Component getMsptInfo(RollingAverage[] averages) {
        final TextComponent.Builder builder = Component.text();

        for (int i = 0; i < averages.length; i++) {
            final double average = averages[i].mean();
            final TextColor color = calculateMsptColor(average);
            builder.append(Component.text(String.format("%.2fms", average), color));
            if (i < averages.length - 1) builder.append(VALUE_SEPARATOR);
        }

        return builder.build();
    }

    private TextColor calculateTpsColor(double amount) {
        float lerpDiv = (float) (amount / TPS);
        return TextColor.lerp(lerpDiv, NamedTextColor.RED, NamedTextColor.GREEN);
    }

    private TextColor calculateMsptColor(double average) {
        final float lerpDiv = (float) (average / MinecraftServer.TICK_MS);
        return TextColor.lerp(lerpDiv, NamedTextColor.GREEN, NamedTextColor.RED);
    }

    private Component createGcComponent() {
        TextComponent.Builder builder = Component.text()
                .append(Component.text("GC Info:", NamedTextColor.GRAY));

        for (Map.Entry<String, GcInfo> entry : this.getGcInfo().entrySet()) {
            TextComponent.Builder entryBuilder = Component.text();
            String lastRunText;
            if (entry.getValue() == null) {
                lastRunText = "never";
            } else {
                long millisSinceRun = this.getUptime() - entry.getValue().getEndTime();
                lastRunText = entry.getValue() == null ? "never" : DurationFormatter.ofGreatestUnit(Duration.ofMillis(millisSinceRun)) + " ago";
            }

            entryBuilder.append(Component.text("\n  " + entry.getKey() + ":", NamedTextColor.GRAY))
                    .append(Component.text("\n    Last Run: ", NamedTextColor.GRAY))
                    .append(Component.text(lastRunText, NamedTextColor.GOLD));

            if (entry.getValue() != null)
                entryBuilder.hoverEvent(HoverEvent.showText(this.createGcHover(entry.getKey(), entry.getValue())));

            builder.append(entryBuilder);
        }

        return builder.asComponent();
    }

    private Component createGcHover(String name, GcInfo info) {
        TextComponent.Builder builder = Component.text()
                .append(Component.text("Name: ", NamedTextColor.GOLD))
                .append(Component.text(name, NamedTextColor.GRAY))
                .append(Component.newline())

                .append(Component.text("Duration: ", NamedTextColor.GOLD))
                .append(Component.text(info.getDuration() + "ms", NamedTextColor.GRAY))
                .append(Component.newline(), Component.newline())

                .append(Component.text("Memory After:", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(this.createMemoryUsagePeriod(info.getMemoryUsageAfterGc()))
                .append(Component.newline(), Component.newline())

                .append(Component.text("Memory Before:", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(this.createMemoryUsagePeriod(info.getMemoryUsageBeforeGc()));
        return builder.build();
    }

    private Component createMemoryUsagePeriod(Map<String, MemoryUsage> memoryUsageMap) {
        List<Component> lines = new ArrayList<>();

        for (Map.Entry<String, MemoryUsage> entry : memoryUsageMap.entrySet()) {
            if (EXCLUDED_MEMORY_SPACES.stream().anyMatch(pattern -> pattern.matcher(entry.getKey()).find()))
                continue;

            lines.add(Component.text().append(Component.text("  " + entry.getKey() + ": ", NamedTextColor.GOLD))
                    .append(Component.text(entry.getValue().getUsed() / 1024 / 1024 + "MB", NamedTextColor.GRAY))
                    .build());
        }

        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private Map<String, GcInfo> getGcInfo() {
        Map<String, GcInfo> gcInfo = new HashMap<>();

        for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            com.sun.management.GarbageCollectorMXBean bean = (com.sun.management.GarbageCollectorMXBean) garbageCollectorMXBean;

            gcInfo.put(bean.getName(), bean.getLastGcInfo());
        }
        return gcInfo;
    }

    private long getUptime() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }
}