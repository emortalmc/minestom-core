package dev.emortal.minestom.core.module.chat;

import dev.emortal.api.message.common.PlayerChatMessageMessage;
import dev.emortal.api.message.messagehandler.ChatMessageCreatedMessage;
import dev.emortal.api.model.messagehandler.ChatMessage;
import dev.emortal.api.modules.ModuleData;
import dev.emortal.api.modules.ModuleEnvironment;
import dev.emortal.api.utils.kafka.FriendlyKafkaProducer;
import dev.emortal.minestom.core.module.MinestomModule;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import dev.emortal.minestom.core.module.permissions.PermissionModule;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.adventure.audience.Audiences;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerChatEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ModuleData(name = "chat", softDependencies = {PermissionModule.class, MessagingModule.class}, required = false)
public final class ChatModule extends MinestomModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatModule.class);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public ChatModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    @Override
    public boolean onLoad() {
        MessagingModule messagingModule = this.moduleManager.getModule(MessagingModule.class);

        if (messagingModule == null || messagingModule.getKafkaProducer() == null) {
            LOGGER.warn("Not enabling ChatModule, MessagingModule or KafkaProducer is null");
            return false;
        }

        FriendlyKafkaProducer kafkaProducer = messagingModule.getKafkaProducer();

        messagingModule.addListener(ChatMessageCreatedMessage.class, message -> {
            ChatMessage chatMessage = message.getMessage();
            Audiences.players().sendMessage(MINI_MESSAGE.deserialize(chatMessage.getMessage(),
                    Placeholder.unparsed("content", chatMessage.getMessageContent())));
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
