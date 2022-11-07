package cc.towerdefence.minestom.module.core;

import cc.towerdefence.minestom.MinestomServer;
import cc.towerdefence.minestom.module.Module;
import cc.towerdefence.minestom.module.ModuleData;
import cc.towerdefence.minestom.module.core.command.CurrentServerCommand;
import cc.towerdefence.minestom.module.core.command.PerformanceCommand;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ModuleData(name = "core", required = false)
public class CoreModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreModule.class);


    public CoreModule(@NotNull EventNode<Event> eventNode) {
        super(eventNode);
    }

    @Override
    public boolean onLoad() {
        if (!MinestomServer.DEV_ENVIRONMENT) {
            PlayerTrackerManager playerTrackerManager = new PlayerTrackerManager(this.eventNode);
            MinecraftServer.getCommandManager().register(new CurrentServerCommand(playerTrackerManager));
        }

        MinecraftServer.getCommandManager().register(new PerformanceCommand(this.eventNode));
        return false;
    }

    @Override
    public void onUnload() {

    }
}
