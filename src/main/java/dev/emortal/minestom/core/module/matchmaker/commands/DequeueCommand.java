package dev.emortal.minestom.core.module.matchmaker.commands;

import com.google.common.util.concurrent.Futures;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;
import dev.emortal.api.kurushimi.DequeueByPlayerErrorResponse;
import dev.emortal.api.kurushimi.DequeueByPlayerRequest;
import dev.emortal.api.kurushimi.MatchmakerGrpc;
import dev.emortal.api.utils.callback.FunctionalFutureCallback;
import io.grpc.protobuf.StatusProto;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ForkJoinPool;

public class DequeueCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(DequeueCommand.class);

    private static final Component DEQUEUE_SUCCESS = Component.text("You have been dequeued", NamedTextColor.GREEN);

    private static final Component DEQUEUE_NOT_IN_QUEUE = Component.text("You are not queued for a game", NamedTextColor.RED);
    private static final Component DEQUEUE_NO_PERMISSION = Component.text("You do not have permission to dequeue", NamedTextColor.RED);
    private static final Component DEQUEUE_ALREADY_MARKED = Component.text("You are already marked for dequeue", NamedTextColor.RED);
    private static final Component DEQUEUE_UNKNOWN_ERROR = Component.text("An unknown error occurred. Please report this to a staff member", NamedTextColor.RED);

    private final @NotNull MatchmakerGrpc.MatchmakerFutureStub matchmaker;

    public DequeueCommand(@NotNull MatchmakerGrpc.MatchmakerFutureStub matchmaker) {
        super("dequeue");

        this.matchmaker = matchmaker;

        this.setCondition((sender, commandString) -> sender instanceof Player);
        this.setDefaultExecutor(this::execute);
    }

    private void execute(CommandSender sender, CommandContext context) {
        Player player = (Player) sender;

        var dequeueReqFuture = this.matchmaker.dequeueByPlayer(
                DequeueByPlayerRequest.newBuilder().setPlayerId(player.getUuid().toString()).build()
        );

        Futures.addCallback(dequeueReqFuture, FunctionalFutureCallback.create(
                response -> player.sendMessage(DEQUEUE_SUCCESS),
                throwable -> {
                    Status status = StatusProto.fromThrowable(throwable);
                    if (status == null || status.getDetailsCount() == 0) {
                        player.sendMessage(DEQUEUE_UNKNOWN_ERROR);
                        LOGGER.error("Failed to dequeue player (id: {}): {}", player.getUuid(), throwable);
                        return;
                    }

                    try {
                        DequeueByPlayerErrorResponse errorResponse = status.getDetails(0).unpack(DequeueByPlayerErrorResponse.class);
                        player.sendMessage(switch (errorResponse.getReason()) {
                            case NOT_IN_QUEUE -> DEQUEUE_NOT_IN_QUEUE;
                            case NO_PERMISSION -> DEQUEUE_NO_PERMISSION;
                            case ALREADY_MARKED_FOR_DEQUEUE -> DEQUEUE_ALREADY_MARKED;
                            default -> {
                                LOGGER.error("Failed to dequeue player for unknown reason (id: {}, errorResponse: {}): {}", player.getUuid(), errorResponse, throwable);
                                yield DEQUEUE_UNKNOWN_ERROR;
                            }
                        });
                    } catch (InvalidProtocolBufferException e) {
                        player.sendMessage(DEQUEUE_UNKNOWN_ERROR);
                        LOGGER.error("Failed to dequeue player (id: {}): {}", player.getUuid(), throwable);
                    }
                }
        ), ForkJoinPool.commonPool());
    }
}
