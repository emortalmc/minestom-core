package dev.emortal.minestom.core.module.matchmaker.commands;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;
import dev.emortal.api.kurushimi.DequeueByPlayerErrorResponse;
import dev.emortal.api.kurushimi.DequeueByPlayerRequest;
import dev.emortal.api.kurushimi.DequeueByPlayerResponse;
import dev.emortal.api.kurushimi.MatchmakerGrpc;
import dev.emortal.minestom.core.module.matchmaker.CommonMatchmakerError;
import io.grpc.protobuf.StatusProto;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ForkJoinPool;

public final class DequeueCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(DequeueCommand.class);

    private final MatchmakerGrpc.MatchmakerFutureStub matchmaker;

    public DequeueCommand(@NotNull MatchmakerGrpc.MatchmakerFutureStub matchmaker) {
        super("dequeue");
        this.matchmaker = matchmaker;

        this.setCondition(Conditions::playerOnly);
        this.setDefaultExecutor(this::execute);
    }

    private void execute(CommandSender sender, CommandContext context) {
        Player player = (Player) sender;
        var request = DequeueByPlayerRequest.newBuilder().setPlayerId(player.getUuid().toString()).build();
        Futures.addCallback(this.matchmaker.dequeueByPlayer(request), new DequeueCallback(player), ForkJoinPool.commonPool());
    }

    private record DequeueCallback(@NotNull Player player) implements FutureCallback<DequeueByPlayerResponse> {

        @Override
        public void onSuccess(@NotNull DequeueByPlayerResponse result) {
            this.player.sendMessage(CommonMatchmakerError.DEQUEUE_SUCCESS);
        }

        @Override
        public void onFailure(@NotNull Throwable throwable) {
            Status status = StatusProto.fromThrowable(throwable);
            if (status == null || status.getDetailsCount() == 0) {
                this.player.sendMessage(CommonMatchmakerError.DEQUEUE_ERR_UNKNOWN);
                LOGGER.error("Failed to dequeue player (id: {})", this.player.getUuid(), throwable);
                return;
            }

            final DequeueByPlayerErrorResponse response;
            try {
                response = status.getDetails(0).unpack(DequeueByPlayerErrorResponse.class);
            } catch (InvalidProtocolBufferException exception) {
                this.player.sendMessage(CommonMatchmakerError.DEQUEUE_ERR_UNKNOWN);
                LOGGER.error("Failed to dequeue player (id: {})", this.player.getUuid(), throwable);
                return;
            }

            var message = switch (response.getReason()) {
                case NOT_IN_QUEUE -> CommonMatchmakerError.DEQUEUE_ERR_NOT_IN_QUEUE;
                case NO_PERMISSION -> CommonMatchmakerError.DEQUEUE_ERR_NO_PERMISSION;
                case ALREADY_MARKED_FOR_DEQUEUE -> CommonMatchmakerError.DEQUEUE_ERR_ALREADY_MARKED;
                default -> {
                    LOGGER.error("Failed to dequeue player for unknown reason (id: {}, errorResponse: {})", this.player.getUuid(), response, throwable);
                    yield CommonMatchmakerError.DEQUEUE_ERR_UNKNOWN;
                }
            };
            this.player.sendMessage(message);
        }
    }
}
