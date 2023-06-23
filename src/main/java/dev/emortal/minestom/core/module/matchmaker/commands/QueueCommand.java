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
    private final List<GameModeConfig> configs;

    public QueueCommand(@NotNull MatchmakerGrpc.MatchmakerFutureStub matchmaker, @NotNull GameModeCollection gameModeCollection) {
        super("play", "queue");
        this.matchmaker = matchmaker;

        // Load the available gamemodes
        this.configs = gameModeCollection.getAllConfigs(this::handleUpdate);

        setCondition(Conditions::playerOnly);

        final ArgumentStringArray modeArg = new ArgumentStringArray("mode");
        modeArg.setSuggestionCallback((sender, context, suggestion) -> {
            final String inputLower = context.getRaw("mode").toLowerCase();

            Stream<String> stream = configs.stream()
                    .filter(GameModeConfig::isEnabled)
                    .map(GameModeConfig::getFriendlyName);

            if (!inputLower.isEmpty() && !inputLower.isBlank() && inputLower.charAt(0) != 0) {
                stream = stream.filter(name -> name.toLowerCase().startsWith(inputLower));
            }

            stream.map(SuggestionEntry::new).forEach(suggestion::addEntry);
        });

        addSyntax(this::execute, modeArg);
    }

    private void execute(@NotNull CommandSender sender, @NotNull CommandContext context) {
        final Player player = (Player) sender;
        final String[] modeArg = context.get("mode");
        final String modeName = String.join(" ", modeArg);

        final GameModeConfig mode = configs.stream()
                .filter(GameModeConfig::isEnabled)
                .filter(config -> config.getFriendlyName().equalsIgnoreCase(modeName))
                .findFirst()
                .orElse(null);

        if (mode == null) {
            sender.sendMessage("Invalid mode " + modeName + "!");
            return;
        }

        final var request = QueueByPlayerRequest.newBuilder()
                .setPlayerId(player.getUuid().toString())
                .setGameModeId(mode.getId())
                .build();

        Futures.addCallback(matchmaker.queueByPlayer(request), new QueueCallback(sender, mode), ForkJoinPool.commonPool());
    }

    private record QueueCallback(@NotNull CommandSender sender, @NotNull GameModeConfig mode) implements FutureCallback<QueueByPlayerResponse> {

        @Override
        public void onSuccess(@NotNull QueueByPlayerResponse result) {
            final var modeName = Placeholder.unparsed("mode", mode.getFriendlyName());
            sender.sendMessage(MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_SUCCESS, modeName));
        }

        @Override
        public void onFailure(@NotNull Throwable throwable) {
            final Status status = StatusProto.fromThrowable(throwable);
            if (status == null || status.getDetailsCount() == 0) {
                sender.sendMessage("An unknown error occurred while queuing for " + mode.getFriendlyName());
                LOGGER.error("An unknown error occurred while queuing for " + mode.getFriendlyName(), throwable);
                return;
            }

            final QueueByPlayerErrorResponse response;
            try {
                response = status.getDetails(0).unpack(QueueByPlayerErrorResponse.class);
            } catch (final InvalidProtocolBufferException exception) {
                sender.sendMessage("An unknown error occurred while queuing for " + mode.getFriendlyName());
                LOGGER.error("An unknown error occurred while queuing for " + mode.getFriendlyName(), exception);
                return;
            }

            final var modeName = Placeholder.unparsed("mode", mode.getFriendlyName());
            final var message = switch (response.getReason()) {
                case ALREADY_IN_QUEUE -> CommonMatchmakerError.QUEUE_ERR_ALREADY_IN_QUEUE;
                case NO_PERMISSION -> MINI_MESSAGE.deserialize(CommonMatchmakerError.PLAYER_PERMISSION_DENIED);
                case INVALID_MAP -> {
                    LOGGER.error("Invalid map for gamemode " + mode.getFriendlyName());
                    yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, modeName);
                }
                case PARTY_TOO_LARGE -> {
                    final var max = Placeholder.unparsed("max", String.valueOf(mode.getPartyRestrictions().getMaxSize()));
                    yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_PARTY_TOO_LARGE, modeName, max);
                }
                case INVALID_GAME_MODE -> {
                    LOGGER.error("Invalid gamemode " + mode.getFriendlyName());
                    yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, modeName);
                }
                case GAME_MODE_DISABLED -> {
                    LOGGER.error("Gamemode " + mode.getFriendlyName() + " is disabled");
                    yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, modeName);
                }
                default -> {
                    LOGGER.error("An unknown error occurred while queuing for " + mode.getFriendlyName(), throwable);
                    yield MINI_MESSAGE.deserialize(CommonMatchmakerError.QUEUE_ERR_UNKNOWN, modeName);
                }
            };
            sender.sendMessage(message);
        }
    }

    private void handleUpdate(@NotNull ConfigUpdate<GameModeConfig> update) {
        switch (update.getType()) {
            case CREATE -> configs.add(update.getConfig());
            case DELETE -> configs.removeIf(config -> config.getFileName().equals(update.getFileName()));
            case MODIFY -> {
                configs.removeIf(config -> config.getFileName().equals(update.getFileName()));
                configs.add(update.getConfig());
            }
        }
    }
}
