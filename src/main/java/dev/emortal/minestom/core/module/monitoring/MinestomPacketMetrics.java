package dev.emortal.minestom.core.module.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import org.jetbrains.annotations.NotNull;

public final class MinestomPacketMetrics implements MeterBinder {

    private final @NotNull EventNode<Event> eventNode;

    public MinestomPacketMetrics(@NotNull EventNode<Event> eventNode) {
        this.eventNode = eventNode;
    }

    @Override
    public void bindTo(@NotNull MeterRegistry registry) {
        Counter packetsSent = Counter.builder("minestom.packets")
                .tag("direction", "out")
                .description("The amount of packets sent by the server")
                .register(registry);

        Counter packetsReceived = Counter.builder("minestom.packets")
                .tag("direction", "in")
                .description("The amount of packets received by the server")
                .register(registry);

        this.eventNode.addListener(PlayerPacketEvent.class, event -> packetsReceived.increment());
        this.eventNode.addListener(PlayerPacketOutEvent.class, event -> packetsSent.increment());
    }
}
