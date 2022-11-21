package cc.towerdefence.minestom.module.kubernetes.command.currentserver;

import cc.towerdefence.minestom.module.kubernetes.PlayerTrackerManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

public class CurrentServerCommand extends Command {
    private final PlayerTrackerManager playerTrackerManager;
    private static final String MESSAGE = """
            <dark_purple>Current Proxy: <light_purple><proxy_id>
            <dark_purple>Current Server: <light_purple><server_id>""";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public CurrentServerCommand(PlayerTrackerManager playerTrackerManager) {
        super("currentserver");

        this.playerTrackerManager = playerTrackerManager;

        this.setDefaultExecutor((sender, context) -> {
            Player player = (Player) sender;
            this.playerTrackerManager.retrievePlayerServer(player.getUuid(), onlineServer -> {
                sender.sendMessage(MINI_MESSAGE.deserialize(MESSAGE,
                        Placeholder.unparsed("server_id", onlineServer.getServerId()),
                        Placeholder.unparsed("proxy_id", onlineServer.getProxyId()))
                );
            });
        });
    }
}
