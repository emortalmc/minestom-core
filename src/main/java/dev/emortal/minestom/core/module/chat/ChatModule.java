package dev.emortal.minestom.core.module.chat;

import dev.emortal.api.message.common.PlayerChatMessageMessage;
import dev.emortal.api.message.messagehandler.ChatMessageCreatedMessage;
import dev.emortal.api.model.messagehandler.ChatMessage;
import dev.emortal.api.modules.annotation.Dependency;
import dev.emortal.api.modules.annotation.ModuleData;
import dev.emortal.api.modules.env.ModuleEnvironment;
import dev.emortal.api.utils.kafka.FriendlyKafkaProducer;
import dev.emortal.minestom.core.module.MinestomModule;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minestom.server.adventure.audience.Audiences;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerChatEvent;
import org.jetbrains.annotations.NotNull;

@ModuleData(name = "chat", dependencies = {@Dependency(name = "messaging")})
public final class ChatModule extends MinestomModule {

    public ChatModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    @Override
    public boolean onLoad() {
        MessagingModule messagingModule = this.getModule(MessagingModule.class);

        messagingModule.addListener(ChatMessageCreatedMessage.class, messageEvent -> {
            ChatMessage message = messageEvent.getMessage();

            TagResolver content;
            if (message.getParseMessageContent())
                content = Placeholder.parsed("content", message.getMessageContent());
            else
                content = Placeholder.unparsed("content", message.getMessageContent());

            Audiences.players().sendMessage(MiniMessage.miniMessage().deserialize(message.getMessage(), content));
        });

        FriendlyKafkaProducer kafkaProducer = messagingModule.getKafkaProducer();

        this.eventNode.addListener(PlayerChatEvent.class, event -> {
            event.setCancelled(true);

            Player player = event.getPlayer();
            var message = ChatMessage.newBuilder()
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
