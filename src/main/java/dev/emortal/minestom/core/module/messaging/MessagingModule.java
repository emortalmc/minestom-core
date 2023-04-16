package dev.emortal.minestom.core.module.messaging;

import com.google.protobuf.AbstractMessage;
import dev.emortal.api.utils.kafka.FriendlyKafkaConsumer;
import dev.emortal.api.utils.kafka.KafkaSettings;
import dev.emortal.api.utils.parser.MessageProtoConfig;
import dev.emortal.api.utils.parser.ProtoParserRegistry;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.module.Module;
import dev.emortal.minestom.core.module.ModuleData;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import dev.emortal.minestom.core.utils.EnvUtils;
import dev.emortal.minestom.core.utils.PortUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Increment this version when you make a change to this class. Sync it with Velocity's version where necessary.
 * @version 1
 */
@ModuleData(name = "messaging", required = true)
public final class MessagingModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessagingModule.class);
    
    private static final String KAFKA_HOST = EnvUtils.getOrDefaultUnlessProd("KAFKA_HOST", "127.0.0.1");
    private static final String KAFKA_PORT = EnvUtils.getOrDefaultUnlessProd("KAFKA_PORT", "9092");

    private final RabbitMqCore rabbitMqCore;
    private final @Nullable FriendlyKafkaConsumer kafkaConsumer;

    public MessagingModule(@NotNull ModuleEnvironment environment) {
        super(environment);

        this.rabbitMqCore = new RabbitMqCore();

        if (!Environment.isProduction() && !PortUtils.isPortUsed(KAFKA_HOST, Integer.parseInt(KAFKA_PORT))) {
            LOGGER.warn("Kafka is not available, disabling Kafka consumer");
            
            this.kafkaConsumer = null;
            return;
        }
        
        KafkaSettings kafkaSettings = new KafkaSettings()
                .setAutoCommit(true)
                .setBootstrapServers(KAFKA_HOST + ":" + KAFKA_PORT);

        this.kafkaConsumer = new FriendlyKafkaConsumer(kafkaSettings);
    }

    public <T extends AbstractMessage> void addListener(Class<T> messageType, Consumer<T> listener) {
        MessageProtoConfig<T> parser = ProtoParserRegistry.getParser(messageType);
        if (parser == null) throw new IllegalArgumentException("No parser found for message type " + messageType.getName());

        switch (parser.service()) {
            case KAFKA -> {
                if (this.kafkaConsumer != null) this.kafkaConsumer.addListener(messageType, listener);
            }
            case RABBIT_MQ -> this.rabbitMqCore.addListener(messageType, listener);
            default -> throw new IllegalStateException("Unexpected value: " + parser.service());
        }
    }

    @Override
    public boolean onLoad() {
        // TODO should we do a health check here?
        return true;
    }

    @Override
    public void onUnload() {
        this.rabbitMqCore.shutdown();
        if (this.kafkaConsumer != null) this.kafkaConsumer.close();
    }
}
