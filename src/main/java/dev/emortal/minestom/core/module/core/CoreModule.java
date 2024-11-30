package dev.emortal.minestom.core.module.core;

import dev.emortal.api.modules.annotation.ModuleData;
import dev.emortal.api.modules.env.ModuleEnvironment;
import dev.emortal.api.service.mcplayer.McPlayerService;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.minestom.core.module.MinestomModule;
import dev.emortal.minestom.core.module.core.badge.BadgeCommand;
import dev.emortal.minestom.core.module.core.performance.PerformanceCommand;
import dev.emortal.minestom.core.module.core.playerprovider.EmortalPlayerImpl;
import dev.emortal.minestom.core.utils.resolver.PlayerResolver;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import org.jetbrains.annotations.NotNull;

@ModuleData(name = "core")
public final class CoreModule extends MinestomModule {

    public CoreModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    @Override
    public boolean onLoad() {
        MinecraftServer.getConnectionManager().setPlayerProvider(EmortalPlayerImpl::new);

        McPlayerService playerService = GrpcStubCollection.getPlayerService().orElse(null);
        PlayerResolver playerResolver = new PlayerResolver(playerService, MinecraftServer.getConnectionManager());

        CommandManager commandManager = MinecraftServer.getCommandManager();
        commandManager.register(new PerformanceCommand(this.eventNode));
        GrpcStubCollection.getBadgeManagerService()
                .ifPresent(badgeService -> commandManager.register(new BadgeCommand(playerService, playerResolver, badgeService)));

        return true;
    }

    @Override
    public void onUnload() {
    }
}
