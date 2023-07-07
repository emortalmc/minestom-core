package dev.emortal.minestom.core.module.matchmaker.commands;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;
import dev.emortal.api.kurushimi.MatchmakerGrpc;
import dev.emortal.api.kurushimi.QueueByPlayerErrorResponse;
import dev.emortal.api.kurushimi.QueueByPlayerRequest;
import dev.emortal.api.kurushimi.QueueByPlayerResponse;
import dev.emortal.api.liveconfigparser.configs.ConfigUpdate;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import dev.emortal.api.utils.callback.FunctionalFutureCallback;
import dev.emortal.minestom.core.module.matchmaker.CommonMatchmakerError;
import io.grpc.protobuf.StatusProto;
import java.util.Collection;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;

public class QueueCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueCommand.class);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final MatchmakerGrpc.MatchmakerFutureStub matchmaker;
    private final Collection<GameModeConfig> configs;

    public QueueCommand(@NotNull MatchmakerGrpc.MatchmakerFutureStub matchmaker, @NotNull GameModeCollection gameModeCollection) {
        super("play", "queue");
        this.matchmaker = matchmaker;
        this.configs = gameModeCollection.getAllConfigs();

        this.setCondition(Conditions::playerOnly);

        var modeArg = new ArgumentStringArray("mode");
        modeArg.setSuggestionCallback((sender, context, suggestion) -> {
            String inputLower = context.getRaw("mode").toLowerCase();

            Stream<String> configNames = this.configs.stream()
                    .filter(GameModeConfig::enabled)
                    .map(GameModeConfig::friendlyName);

            if (!inputLower.isEmpty() && !inputLower.isBlank() && inputLower.charAt(0) != 0) {
                configNames = configNames.filter(name -> name.toLowerCase().startsWith(inputLower));
            }

            configNames.map(SuggestionEntry::new).forEach(suggestion::addEntry);
        });

        this.addSyntax(this::execute, modeArg);
    }

    private void execute(@NotNull CommandSender sender, @NotNull CommandContext context) {
        Player player = (Player) sender;
        String[] modeArg = context.get("mode");
        String modeName = String.join(" ", modeArg);

        GameModeConfig mode = this.configs.stream()
                .filter(GameModeConfig::enabled)
                .filter(config -> config.friendlyName().equalsIgnoreCase(modeName))
                .findFirst()
                .orElse(null);

        if (mode == null) {
            sender.sendMessage("Invalid mode " + modeName + "!");
            return;
        }

        var request = QueueByPlayerRequest.newBuilder()
                .setPlayerId(player.getUuid().toString())
                .setGameModeId(mode.id())
                .build();

        Futures.addCallback(this.matchmaker.queueByPlayer(request), new QueueCallback(sender, mode), ForkJoinPool.commonPool());
    }

    private record QueueCallback(@NotNull CommandSender sender, @NotNull GameModeConfig mode) implements FutureCallback<QueueByPlayerResponse> {

        @Override
        public void onSuccess(@NotNull QueueByPlayerResponse result) {
            var modeName = Placeholder.unparsed("mode", this.mode.friendlyName());
            this.sender.sendMessage(MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_SUCCESS, modeName));
        }

        @Override
        public void onFailure(@NotNull Throwable throwable) {
            Status status = StatusProto.fromThrowable(throwable);
            if (status == null || status.getDetailsCount() == 0) {
                this.sender.sendMessage("An unknown error occurred while queuing for " + this.mode.friendlyName());
                LOGGER.error("An unknown error occurred while queuing for " + this.mode.friendlyName(), throwable);
                return;
            }

            final QueueByPlayerErrorResponse response;
            try {
                response = status.getDetails(0).unpack(QueueByPlayerErrorResponse.class);
            } catch (InvalidProtocolBufferException exception) {
                this.sender.sendMessage("An unknown error occurred while queuing for " + this.mode.friendlyName());
                LOGGER.error("An unknown error occurred while queuing for " + this.mode.friendlyName(), exception);
                return;
            }

            var modeName = Placeholder.unparsed("mode", this.mode.friendlyName());
            var message = switch (response.getReason()) {
                case ALREADY_IN_QUEUE -> CommonMatchmakerError.QUEUE_ERR_ALREADY_IN_QUEUE;
                case NO_PERMISSION -> MINI_MESSAGE.deserialize(CommonMatchmakerError.PLAYER_PERMISSION_DENIED);
                case INVALID_MAP -> {
                    LOGGER.error("Invalid map for gamemode " + this.mode.friendlyName());
                    yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, modeName);
                }
                case PARTY_TOO_LARGE -> {
                    final var max = Placeholder.unparsed("max", String.valueOf(this.mode.partyRestrictions().maxSize()));
                    yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_PARTY_TOO_LARGE, modeName, max);
                }
                case INVALID_GAME_MODE -> {
                    LOGGER.error("Invalid gamemode " + this.mode.friendlyName());
                    yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, modeName);
                }
                case GAME_MODE_DISABLED -> {
                    LOGGER.error("Gamemode " + this.mode.friendlyName() + " is disabled");
                    yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, modeName);
                }
                default -> {
                    LOGGER.error("An unknown error occurred while queuing for " + this.mode.friendlyName(), throwable);
                    yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, modeName);
                }
            };
            this.sender.sendMessage(message);
        }
    }
}
