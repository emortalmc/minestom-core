package dev.emortal.minestom.core.module.chat;

import dev.emortal.api.message.common.PlayerChatMessageMessage;
import dev.emortal.api.message.messagehandler.ChatMessageCreatedMessage;
import dev.emortal.api.model.messagehandler.ChatMessage;
import dev.emortal.api.utils.kafka.FriendlyKafkaProducer;
import dev.emortal.minestom.core.module.Module;
import dev.emortal.minestom.core.module.ModuleData;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import dev.emortal.minestom.core.module.permissions.PermissionModule;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.adventure.audience.Audiences;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerChatEvent;
import org.jetbrains.annotations.NotNull;

@ModuleData(name = "chat", softDependencies = {PermissionModule.class, MessagingModule.class}, required = false)
public final class ChatModule extends Module {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public ChatModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    @Override
    public boolean onLoad() {
        MessagingModule messagingModule = this.moduleManager.getModule(MessagingModule.class);
        FriendlyKafkaProducer kafkaProducer = messagingModule.getKafkaProducer();

        if (kafkaProducer == null) return false;

        messagingModule.addListener(ChatMessageCreatedMessage.class, message -> {
            ChatMessage chatMessage = message.getMessage();
            Audiences.all().sendMessage(MINI_MESSAGE.deserialize(chatMessage.getMessage()));
        });

        this.eventNode.addListener(PlayerChatEvent.class, event -> {
            event.setCancelled(true);

            Player player = event.getPlayer();
            kafkaProducer.produceAndForget(PlayerChatMessageMessage.newBuilder()
                    .setMessage(
                            ChatMessage.newBuilder()
                                    .setMessage(event.getMessage())
                                    .setSenderId(player.getUuid().toString())
                                    .setSenderUsername(player.getUsername())
                    ).build());
        });
        return true;
    }

    @Override
    public void onUnload() {

    }
}
