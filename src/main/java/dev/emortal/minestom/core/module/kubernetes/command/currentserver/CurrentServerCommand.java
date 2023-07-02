package dev.emortal.minestom.core.module.kubernetes.command.currentserver;

import com.google.common.util.concurrent.Futures;
import dev.emortal.api.grpc.mcplayer.McPlayerProto;
import dev.emortal.api.grpc.mcplayer.PlayerTrackerGrpc;
import dev.emortal.api.model.mcplayer.CurrentServer;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.api.utils.callback.FunctionalFutureCallback;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ForkJoinPool;

public final class CurrentServerCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurrentServerCommand.class);

    private static final String MESSAGE = """
            <dark_purple>Proxy: <light_purple><proxy_id>
            <dark_purple>Server: <light_purple><server_id>""";
    private static final String COPY_MESSAGE = """
            Proxy: %s
            Server: %s
            Instance: %s
            Position: %s""";

    private final PlayerTrackerGrpc.PlayerTrackerFutureStub playerTracker = GrpcStubCollection.getPlayerTrackerService().orElse(null);

    public CurrentServerCommand() {
        super("whereami");

        this.setCondition(Conditions::playerOnly);
        this.setDefaultExecutor(this::onExecute);
    }

    private void onExecute(CommandSender sender, CommandContext context) {
        Player player = (Player) sender;

        if (this.playerTracker == null) {
            sender.sendMessage(Component.text("Player tracker service is not available, cannot get your current server. Please try again later.", NamedTextColor.RED));
            return;
        }

        var request = McPlayerProto.GetPlayerServersRequest.newBuilder().addPlayerIds(player.getUuid().toString()).build();
        Futures.addCallback(this.playerTracker.getPlayerServers(request), FunctionalFutureCallback.create(
                response -> {
                    CurrentServer currentServer = response.getPlayerServersMap().get(player.getUuid().toString());

                    var serverId = Placeholder.unparsed("server_id", currentServer.getServerId());
                    var proxyId = Placeholder.unparsed("proxy_id", currentServer.getProxyId());

                    var message = MiniMessage.miniMessage().deserialize(MESSAGE, serverId, proxyId)
                            .clickEvent(ClickEvent.copyToClipboard(createCopyableData(currentServer, player)))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to copy", NamedTextColor.GREEN)));
                    sender.sendMessage(message);
                },
                exception -> LOGGER.error("Failed to retrieve player server", exception)
        ), ForkJoinPool.commonPool());
    }

    private String createCopyableData(@NotNull CurrentServer server, @NotNull Player player) {
        return COPY_MESSAGE.formatted(
                server.getProxyId(),
                server.getServerId(),
                player.getInstance().getUniqueId(),
                this.formatPos(player.getPosition())
        );
    }

    private String formatPos(@NotNull Pos pos) {
        return String.format(
                "x: %.2f, y: %.2f, z: %.2f, yaw: %.2f, pitch: %.2f",
                pos.x(), pos.y(), pos.z(), pos.yaw(), pos.pitch()
        );
    }
}