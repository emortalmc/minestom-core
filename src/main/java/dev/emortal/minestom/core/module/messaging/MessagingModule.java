package dev.emortal.minestom.core.module.messaging;

import com.google.protobuf.AbstractMessage;
import dev.emortal.api.modules.Module;
import dev.emortal.api.modules.annotation.ModuleData;
import dev.emortal.api.modules.env.ModuleEnvironment;
import dev.emortal.api.utils.kafka.FriendlyKafkaConsumer;
import dev.emortal.api.utils.kafka.FriendlyKafkaProducer;
import dev.emortal.api.utils.kafka.KafkaSettings;
import dev.emortal.api.utils.parser.MessageProtoConfig;
import dev.emortal.api.utils.parser.ProtoParserRegistry;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.utils.EnvUtils;
import dev.emortal.minestom.core.utils.PortUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Increment this version when you make a change to this class. Sync it with Velocity's version where necessary.
 *
 * @version 1
 */
@ModuleData(name = "messaging")
public final class MessagingModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessagingModule.class);

    private static final String KAFKA_HOST = EnvUtils.getOrDefaultUnlessProd("KAFKA_HOST", "127.0.0.1");
    private static final String KAFKA_PORT = EnvUtils.getOrDefaultUnlessProd("KAFKA_PORT", "9092");

    private @Nullable FriendlyKafkaConsumer kafkaConsumer;
    private @Nullable FriendlyKafkaProducer kafkaProducer;

    public MessagingModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    public <T extends AbstractMessage> void addListener(@NotNull Class<T> messageType, @NotNull Consumer<T> listener) {
        MessageProtoConfig<T> parser = ProtoParserRegistry.getParser(messageType);
        if (parser == null) {
            throw new IllegalArgumentException("No parser found for message type " + messageType.getName());
        }

        if (this.kafkaConsumer != null) this.kafkaConsumer.addListener(messageType, listener);
    }

    public @Nullable FriendlyKafkaProducer getKafkaProducer() {
        return this.kafkaProducer;
    }

    @Override
    public boolean onLoad() {
        if (!Environment.isProduction() && !PortUtils.isPortUsed(KAFKA_HOST, Integer.parseInt(KAFKA_PORT))) {
            LOGGER.warn("Kafka is not available, disabling Kafka consumer and producer");
            return false;
        }

        KafkaSettings settings = KafkaSettings.builder().bootstrapServers(KAFKA_HOST + ":" + KAFKA_PORT).build();
        this.kafkaConsumer = new FriendlyKafkaConsumer(settings);
        this.kafkaProducer = new FriendlyKafkaProducer(settings);
        return true;
    }

    @Override
    public void onUnload() {
        if (this.kafkaConsumer != null) this.kafkaConsumer.close();
        if (this.kafkaProducer != null) this.kafkaProducer.shutdown();
    }
}
