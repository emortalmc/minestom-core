package dev.emortal.minestom.core.module.core.command;

import com.google.common.util.concurrent.Futures;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;
import dev.emortal.api.grpc.badge.BadgeManagerGrpc;
import dev.emortal.api.grpc.badge.BadgeManagerProto;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.api.utils.callback.FunctionalFutureCallback;
import dev.emortal.minestom.core.utils.command.argument.ArgumentBadge;
import io.grpc.protobuf.StatusProto;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ForkJoinPool;

public class BadgeCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeCommand.class);

    private final BadgeManagerGrpc.BadgeManagerFutureStub badgeManager = GrpcStubCollection.getBadgeManagerService().orElse(null);

    public BadgeCommand() {
        super("badge");

        ArgumentLiteral setArgument = new ArgumentLiteral("set");
        ArgumentWord badgeArgument = ArgumentBadge.create(this.badgeManager, "badge", true);

        this.addConditionalSyntax(Conditions::playerOnly, this::executeSetCurrentBadge, setArgument, badgeArgument);
    }

    private void executeSetCurrentBadge(CommandSender sender, CommandContext context) {
        String badgeId = context.get("badge");
        Player player = (Player) sender;

        var setBadgeReqFuture = this.badgeManager.setActivePlayerBadge(BadgeManagerProto.SetActivePlayerBadgeRequest.newBuilder()
                .setBadgeId(badgeId)
                .setPlayerId(player.getUuid().toString()).build());

        Futures.addCallback(setBadgeReqFuture, FunctionalFutureCallback.create(
                        response -> sender.sendMessage(Component.text("Set your badge to " + badgeId)),
                        throwable -> {
                            Status status = StatusProto.fromThrowable(throwable);
                            if (status == null || status.getDetailsCount() == 0) {
                                LOGGER.error("Failed to set badge", throwable);
                                return;
                            }

                            try {
                                BadgeManagerProto.SetActivePlayerBadgeErrorResponse errorResponse = status.getDetails(0).unpack(BadgeManagerProto.SetActivePlayerBadgeErrorResponse.class);
                                switch (errorResponse.getReason()) {
                                    case PLAYER_DOESNT_HAVE_BADGE ->
                                            sender.sendMessage(Component.text("You don't have that badge"));
                                    default -> {
                                        LOGGER.error("Failed to set badge", throwable);
                                        sender.sendMessage(Component.text("Failed to set badge"));
                                    }
                                }
                            } catch (InvalidProtocolBufferException e) {
                                LOGGER.error("Failed to set badge", throwable);
                            }
                        }
                ), ForkJoinPool.commonPool()
        );
    }
}
