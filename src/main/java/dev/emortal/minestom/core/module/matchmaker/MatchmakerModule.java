package dev.emortal.minestom.core.module.matchmaker;

import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.modules.annotation.Dependency;
import dev.emortal.api.modules.annotation.ModuleData;
import dev.emortal.api.modules.env.ModuleEnvironment;
import dev.emortal.api.service.matchmaker.MatchmakerService;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.minestom.core.module.MinestomModule;
import dev.emortal.minestom.core.module.liveconfig.LiveConfigModule;
import dev.emortal.minestom.core.module.matchmaker.commands.DequeueCommand;
import dev.emortal.minestom.core.module.matchmaker.commands.QueueCommand;
import dev.emortal.minestom.core.module.matchmaker.session.DefaultMatchmakingSessionImpl;
import dev.emortal.minestom.core.module.matchmaker.session.MatchmakingSession;
import dev.emortal.minestom.core.module.matchmaker.session.MatchmakingSessionManager;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ModuleData(name = "matchmaker", dependencies = {@Dependency(name = "messaging"), @Dependency(name = "live-config")})
public final class MatchmakerModule extends MinestomModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchmakerModule.class);

    private final @NotNull MatchmakingSession.Creator sessionCreator;

    public MatchmakerModule(@NotNull ModuleEnvironment environment) {
        this(environment, DefaultMatchmakingSessionImpl::new);
    }

    public MatchmakerModule(@NotNull ModuleEnvironment environment, @NotNull MatchmakingSession.Creator sessionCreator) {
        super(environment);
        this.sessionCreator = sessionCreator;
    }

    @Override
    public boolean onLoad() {
        MessagingModule messaging = this.getModule(MessagingModule.class);
        LiveConfigModule liveConfig = this.getModule(LiveConfigModule.class);

        MatchmakerService service = GrpcStubCollection.getMatchmakerService().orElse(null);
        if (service == null) {
            LOGGER.error("Matchmaker service unavailable. Matchmaking features will not work.");
            return false;
        }

        GameModeCollection gameModes = liveConfig.getGameModes();
        if (gameModes == null) {
            LOGGER.error("Game modes unavailable. Matchmaking features will not work.");
            return false;
        }

        CommandManager commandManager = MinecraftServer.getCommandManager();
        commandManager.register(new QueueCommand(service, gameModes));
        commandManager.register(new DequeueCommand(service));

        new MatchmakingSessionManager(this.eventNode, service, messaging, gameModes, this.sessionCreator);

        return true;
    }

    @Override
    public void onUnload() {
    }
}
