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
        final MessagingModule messagingModule = getModule(MessagingModule.class);

        if (messagingModule == null || messagingModule.getKafkaProducer() == null) {
            LOGGER.warn("Not enabling ChatModule, MessagingModule or KafkaProducer is null");
            return false;
        }

        messagingModule.addListener(ChatMessageCreatedMessage.class, message -> {
            final ChatMessage chatMessage = message.getMessage();
            final var content = Placeholder.unparsed("content", chatMessage.getMessageContent());
            Audiences.players().sendMessage(MINI_MESSAGE.deserialize(chatMessage.getMessage(), content));
        });

        final FriendlyKafkaProducer kafkaProducer = messagingModule.getKafkaProducer();

        eventNode.addListener(PlayerChatEvent.class, event -> {
            event.setCancelled(true);

            final Player player = event.getPlayer();
            final ChatMessage message = ChatMessage.newBuilder()
                    .setMessage(event.getMessage())
                    .setSenderId(player.getUuid().toString())
                    .setSenderUsername(player.getUsername())
                    .build();
            kafkaProducer.produceAndForget(PlayerChatMessageMessage.newBuilder().setMessage(message).build());
        });
        return true;
    }

    @Override
    public void onUnload() {
    }
}
