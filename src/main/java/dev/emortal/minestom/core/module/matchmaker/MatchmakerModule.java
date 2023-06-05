package dev.emortal.minestom.core.module.matchmaker;

import dev.emortal.api.kurushimi.KurushimiStubCollection;
import dev.emortal.api.kurushimi.KurushimiUtils;
import dev.emortal.api.kurushimi.MatchmakerGrpc;
import dev.emortal.api.kurushimi.Ticket;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import dev.emortal.api.modules.ModuleData;
import dev.emortal.api.modules.ModuleEnvironment;
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
import net.minestom.server.entity.Player;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@ModuleData(name = "matchmaker", required = false, softDependencies = {MessagingModule.class, LiveConfigModule.class})
public final class MatchmakerModule extends MinestomModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchmakerModule.class);

    private final MatchmakerGrpc.MatchmakerFutureStub matchmakerStub = KurushimiStubCollection.getFutureStub().orElse(null);

    private final MessagingModule messaging;
    private final LiveConfigModule liveConfig;

    private final @NotNull TriFunction<Player, GameModeConfig, Ticket, MatchmakingSession> sessionCreator;
    private MatchmakingSessionManager matchmakingSessionManager;

    public MatchmakerModule(@NotNull ModuleEnvironment environment) {
        this(environment, DefaultMatchmakingSessionImpl::new);
    }

    public MatchmakerModule(@NotNull ModuleEnvironment environment, @NotNull TriFunction<Player, GameModeConfig, Ticket, MatchmakingSession> sessionCreator) {
        super(environment);

        KurushimiUtils.registerParserRegistry();

        this.messaging = environment.moduleManager().getModule(MessagingModule.class);
        this.liveConfig = environment.moduleManager().getModule(LiveConfigModule.class);

        this.sessionCreator = sessionCreator;
    }

    @Override
    public boolean onLoad() {
        if (this.matchmakerStub == null) {
            LOGGER.error("Matchmaker gRPC stub is not present but is required for MatchmakerModule");
            return false;
        }

        Optional<GameModeCollection> gameModeCollection = this.liveConfig.getConfigCollection().gameModes();
        if (gameModeCollection.isEmpty()) {
            LOGGER.error("GameModeCollection is not present in LiveConfigModule but is required for MatchmakerModule");
            return false;
        }

        CommandManager commandManager = MinecraftServer.getCommandManager();
        commandManager.register(new QueueCommand(this.matchmakerStub, gameModeCollection.get()));
        commandManager.register(new DequeueCommand(this.matchmakerStub));

        this.matchmakingSessionManager = new MatchmakingSessionManager(
                this.eventNode, this.matchmakerStub, this.messaging, gameModeCollection.get(), this.sessionCreator
        );

        return true;
    }

    @Override
    public void onUnload() {

    }
}
