package cc.towerdefence.minestom.module;

import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;

public record ModuleEnvironment(@NotNull EventNode<Event> eventNode, @NotNull ModuleManager moduleManager) {

}
