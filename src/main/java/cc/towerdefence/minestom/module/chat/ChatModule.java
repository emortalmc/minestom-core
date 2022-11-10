package cc.towerdefence.minestom.module.chat;

import cc.towerdefence.minestom.MinestomServer;
import cc.towerdefence.minestom.module.Module;
import cc.towerdefence.minestom.module.ModuleData;
import cc.towerdefence.minestom.module.permissions.PermissionCache;
import cc.towerdefence.minestom.module.permissions.PermissionModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerChatEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@ModuleData(name = "chat", required = false, dependencies = PermissionModule.class)
public class ChatModule extends Module {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public ChatModule(@NotNull EventNode<Event> eventNode) {
        super(eventNode);
    }

    @Override
    public boolean onLoad() {
        PermissionModule permissionModule = MinestomServer.getModule(PermissionModule.class);
        PermissionCache permissionCache = permissionModule.getPermissionCache();

        this.eventNode.addListener(PlayerChatEvent.class, event -> {
            Player player = event.getPlayer();
            event.setChatFormat(unused -> {
                Optional<PermissionCache.User> optionalUser = permissionCache.getUser(player.getUuid());
                if (optionalUser.isEmpty()) return Component.text(event.getMessage());

                PermissionCache.User user = optionalUser.get();
                return user.getDisplayPrefix()
                        .append(Component.text(" "))
                        .append(MINI_MESSAGE.deserialize(user.getDisplayName(), Placeholder.unparsed("username", player.getUsername())))
                        .append(Component.text(": "))
                        .append(Component.text(event.getMessage()));
            });
        });
        return false;
    }

    @Override
    public void onUnload() {

    }
}
