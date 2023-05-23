package dev.emortal.minestom.core.module.core.badge;

import com.google.common.util.concurrent.Futures;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;
import dev.emortal.api.grpc.badge.BadgeManagerGrpc;
import dev.emortal.api.grpc.badge.BadgeManagerProto;
import dev.emortal.api.grpc.mcplayer.McPlayerProto;
import dev.emortal.api.model.mcplayer.McPlayer;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.api.utils.callback.FunctionalFutureCallback;
import dev.emortal.minestom.core.utils.command.argument.ArgumentBadge;
import dev.emortal.minestom.core.utils.command.argument.ArgumentMcPlayer;
import io.grpc.protobuf.StatusProto;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentWord;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

public class BadgeAdminCommand extends Command {
    private final BadgeManagerGrpc.BadgeManagerFutureStub badgeManager = GrpcStubCollection.getBadgeManagerService().orElse(null);

    public BadgeAdminCommand() {
        super("badgeadmin");

        this.setCondition((sender, commandString) -> sender.hasPermission("command.badge.admin"));

        ArgumentLiteral addArgument = new ArgumentLiteral("add");
        ArgumentLiteral removeArgument = new ArgumentLiteral("remove");
        Argument<CompletableFuture<McPlayer>> playerArgument = ArgumentMcPlayer.create("player",
                GrpcStubCollection.getPlayerService().orElse(null),
                McPlayerProto.SearchPlayersByUsernameRequest.FilterMethod.NONE
        );
        ArgumentWord badgeArgument = ArgumentBadge.create(this.badgeManager, "badge", false);

        this.addSyntax(this::executeAddBadgeToPlayer, addArgument, playerArgument, badgeArgument);

        this.addSyntax(this::executeRemoveBadgeFromPlayer, removeArgument, playerArgument, badgeArgument);
    }


    private void executeAddBadgeToPlayer(CommandSender sender, CommandContext context) {
        CompletableFuture<McPlayer> playerFuture = context.get("player");
        String badgeId = context.get("badge");

        playerFuture.thenAccept(mcPlayer -> {
            var addBadgeReqFuture = this.badgeManager.addBadgeToPlayer(BadgeManagerProto.AddBadgeToPlayerRequest.newBuilder()
                    .setBadgeId(badgeId)
                    .setPlayerId(mcPlayer.getId()).build());

            Futures.addCallback(addBadgeReqFuture, FunctionalFutureCallback.create(
                            response -> sender.sendMessage(Component.text("Added badge to player")),
                            throwable -> {
                                Status status = StatusProto.fromThrowable(throwable);
                                if (status == null || status.getDetailsCount() == 0) {
                                    LOGGER.error("Failed to add badge to player", throwable);
                                    return;
                                }

                                try {
                                    BadgeManagerProto.AddBadgeToPlayerErrorResponse errorResponse = status.getDetails(0).unpack(BadgeManagerProto.AddBadgeToPlayerErrorResponse.class);
                                    switch (errorResponse.getReason()) {
                                        case PLAYER_ALREADY_HAS_BADGE ->
                                                sender.sendMessage(Component.text("Player already has that badge"));
                                        default -> {
                                            LOGGER.error("Failed to add badge to player", throwable);
                                            sender.sendMessage(Component.text("Failed to add badge to player"));
                                        }
                                    }
                                } catch (InvalidProtocolBufferException e) {
                                    LOGGER.error("Failed to add badge to player", throwable);
                                }
                            }
                    ), ForkJoinPool.commonPool()
            );
        });
    }

    private void executeRemoveBadgeFromPlayer(CommandSender sender, CommandContext context) {
        CompletableFuture<McPlayer> playerFuture = context.get("player");
        String badgeId = context.get("badge");

        playerFuture.thenAccept(mcPlayer -> {
            var removeBadgeReqFuture = this.badgeManager.removeBadgeFromPlayer(BadgeManagerProto.RemoveBadgeFromPlayerRequest.newBuilder()
                    .setBadgeId(badgeId)
                    .setPlayerId(mcPlayer.getId()).build());

            Futures.addCallback(removeBadgeReqFuture, FunctionalFutureCallback.create(
                            response -> sender.sendMessage(Component.text("Removed badge from player")),
                            throwable -> {
                                Status status = StatusProto.fromThrowable(throwable);
                                if (status == null || status.getDetailsCount() == 0) {
                                    LOGGER.error("Failed to remove badge from player", throwable);
                                    return;
                                }

                                try {
                                    BadgeManagerProto.RemoveBadgeFromPlayerErrorResponse errorResponse = status.getDetails(0).unpack(BadgeManagerProto.RemoveBadgeFromPlayerErrorResponse.class);
                                    switch (errorResponse.getReason()) {
                                        case PLAYER_DOESNT_HAVE_BADGE ->
                                                sender.sendMessage(Component.text("Player doesn't have that badge"));
                                        default -> {
                                            LOGGER.error("Failed to remove badge from player", throwable);
                                            sender.sendMessage(Component.text("Failed to remove badge from player"));
                                        }
                                    }
                                } catch (InvalidProtocolBufferException e) {
                                    LOGGER.error("Failed to remove badge from player", throwable);
                                }
                            }
                    ), ForkJoinPool.commonPool()
            );
        });
    }
}
