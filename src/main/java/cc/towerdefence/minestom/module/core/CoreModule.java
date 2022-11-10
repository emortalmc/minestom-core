package cc.towerdefence.minestom.module.core;

import cc.towerdefence.minestom.module.Module;
import cc.towerdefence.minestom.module.ModuleData;
import cc.towerdefence.minestom.module.core.command.PerformanceCommand;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;

@ModuleData(name = "core", required = false)
public class CoreModule extends Module {

    public CoreModule(@NotNull EventNode<Event> eventNode) {
        super(eventNode);
    }

    @Override
    public boolean onLoad() {
        MinecraftServer.getCommandManager().register(new PerformanceCommand(this.eventNode));
        return true;
    }

    @Override
    public void onUnload() {

    }
}
