package cc.towerdefence.minestom.module.core;

import cc.towerdefence.minestom.module.Module;
import cc.towerdefence.minestom.module.ModuleData;
import cc.towerdefence.minestom.module.ModuleEnvironment;
import cc.towerdefence.minestom.module.core.command.PerformanceCommand;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

@ModuleData(name = "core", required = false)
public class CoreModule extends Module {

    public CoreModule(@NotNull ModuleEnvironment environment) {
        super(environment);
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
