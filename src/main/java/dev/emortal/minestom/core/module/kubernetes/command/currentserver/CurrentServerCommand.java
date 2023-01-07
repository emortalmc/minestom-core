package dev.emortal.minestom.core.module.kubernetes.command.currentserver;

import dev.emortal.api.service.PlayerTrackerProto;
import dev.emortal.minestom.core.module.kubernetes.PlayerTrackerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.command.builder.Command;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CurrentServerCommand extends Command {
    private final PlayerTrackerManager playerTrackerManager;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final String MESSAGE = """
            <dark_purple>Proxy: <light_purple><proxy_id>
            <dark_purple>Server: <light_purple><server_id>""";
    private static final String COPY_MESSAGE = """
            Proxy: %s
            Server: %s
            Instance: %s
            Position: %s""";

    public CurrentServerCommand(PlayerTrackerManager playerTrackerManager) {
        super("whereami");

        this.playerTrackerManager = playerTrackerManager;

        this.setDefaultExecutor((sender, context) -> {
            Player player = (Player) sender;
            this.playerTrackerManager.retrievePlayerServer(player.getUuid(), onlineServer -> {
                sender.sendMessage(MINI_MESSAGE.deserialize(MESSAGE,
                                Placeholder.unparsed("server_id", onlineServer.getServerId()),
                                Placeholder.unparsed("proxy_id", onlineServer.getProxyId()))
                        .clickEvent(ClickEvent.copyToClipboard(
                                this.createCopyableData(onlineServer, player)
                        ))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Click to copy", NamedTextColor.GREEN)
                        ))
                );
            });
        });
    }

    private String createCopyableData(@NotNull PlayerTrackerProto.OnlineServer onlineServer, @NotNull Player player) {
        return COPY_MESSAGE.formatted(
                onlineServer.getProxyId(),
                onlineServer.getServerId(),
                player.getInstance().getUniqueId(),
                this.formatPos(player.getPosition())
        );
    }

    private String formatPos(@NotNull Pos pos) {
        return String.format("x: %s, y: %s, z: %s, yaw: %s, pitch: %s", pos.x(), pos.y(), pos.z(), pos.yaw(), pos.pitch());
    }
}