package cc.towerdefence.minestom.module.chat;

import cc.towerdefence.minestom.module.Module;
import cc.towerdefence.minestom.module.ModuleData;
import cc.towerdefence.minestom.module.ModuleEnvironment;
import cc.towerdefence.minestom.module.permissions.PermissionCache;
import cc.towerdefence.minestom.module.permissions.PermissionModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerChatEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@ModuleData(name = "chat", required = false)
public class ChatModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatModule.class);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final String CHAT_FORMAT = "<prefix><reset> <display_name><reset>: <message>";

    public ChatModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    @Override
    public boolean onLoad() {
        PermissionModule permissionModule = this.moduleManager.getModule(PermissionModule.class);
        PermissionCache permissionCache = permissionModule.getPermissionCache();

        if (permissionCache == null) {
            LOGGER.warn("Permission module is not loaded, chat will not be formatted.");
            return true;
        }

        this.eventNode.addListener(PlayerChatEvent.class, event -> {
            Player player = event.getPlayer();
            event.setChatFormat(unused -> {
                Optional<PermissionCache.User> optionalUser = permissionCache.getUser(player.getUuid());
                if (optionalUser.isEmpty()) return Component.text(event.getMessage());

                PermissionCache.User user = optionalUser.get();
                return MINI_MESSAGE.deserialize(CHAT_FORMAT,
                        Placeholder.component("prefix", user.getDisplayPrefix()),
                        Placeholder.unparsed("username", player.getUsername()),
                        Placeholder.parsed("display_name", user.getDisplayName()),
                        Placeholder.unparsed("message", event.getMessage())
                );
            });
        });
        return true;
    }

    @Override
    public void onUnload() {

    }
}
