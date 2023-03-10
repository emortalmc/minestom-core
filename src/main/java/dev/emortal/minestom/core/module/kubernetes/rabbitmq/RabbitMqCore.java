package dev.emortal.minestom.core.module.kubernetes.rabbitmq;

import com.google.protobuf.AbstractMessage;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import dev.emortal.api.utils.parser.ProtoParserRegistry;
import dev.emortal.minestom.core.Environment;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class RabbitMqCore {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqCore.class);

    private static final String BACKEND_ALL_EXCHANGE = "mc:gameserver:all";

    private static final String HOST = System.getenv("RABBITMQ_HOST");
    private static final String USERNAME = System.getenv("RABBITMQ_USERNAME");
    private static final String PASSWORD = System.getenv("RABBITMQ_PASSWORD");

    private final Map<Class<?>, Consumer<AbstractMessage>> protoListeners = new ConcurrentHashMap<>();
    private final Connection connection;
    private final Channel channel;
    private final String selfQueueName;

    public RabbitMqCore(EventNode<Event> eventNode) {
        // For running in development, so we don't need a fully setup RabbitMQ server just to run everything.
        if (HOST == null || USERNAME == null || PASSWORD == null) {
            LOGGER.warn("RabbitMQ username or password not set, skipping RabbitMQ event listener");
            this.connection = null;
            this.channel = null;
            this.selfQueueName = null;
            return;
        }

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(HOST);
        connectionFactory.setUsername(USERNAME);
        connectionFactory.setPassword(PASSWORD);

        try {
            this.connection = connectionFactory.newConnection();
            this.channel = this.connection.createChannel();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String selfQueueName = null;
        try {
            selfQueueName = this.channel.queueDeclare().getQueue();
            this.channel.queueBind(selfQueueName, BACKEND_ALL_EXCHANGE, "");

            LOGGER.info("Listening for messages on queue {}", selfQueueName);
            this.channel.basicConsume(selfQueueName, true, (consumerTag, delivery) -> {
                final String type = delivery.getProperties().getType();

                final AbstractMessage message = ProtoParserRegistry.parse(type, delivery.getBody());
                final Consumer<AbstractMessage> listener = this.protoListeners.get(message.getClass());

                if (listener == null) {
                    LOGGER.warn("No listener registered for message of type {}!", type);
                } else {
                    listener.accept(message);
                }
            }, consumerTag -> LOGGER.warn("Consumer cancelled"));

        } catch (final IOException exception) {
            LOGGER.error("Failed to bind to backend all exchange!", exception);
        }
        this.selfQueueName = selfQueueName;

        MinecraftServer.getSchedulerManager().buildShutdownTask(this::shutdown);
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractMessage> void addListener(final Class<T> messageType, final Consumer<T> listener) {
        this.protoListeners.put(messageType, (Consumer<AbstractMessage>) listener);
    }

    public void publish(final String channel, final AbstractMessage message) {
        final AMQP.BasicProperties properties = createPropertiesWithType(channel);
        try {
            this.channel.basicPublish(BACKEND_ALL_EXCHANGE, "", properties, message.toByteArray());
        } catch (final IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private AMQP.BasicProperties createPropertiesWithType(final String type) {
        return new AMQP.BasicProperties.Builder()
                .timestamp(new Date())
                .type(type)
                .appId(Environment.getHostname())
                .build();
    }

    public void shutdown() {
        try {
            this.channel.close();
            this.connection.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
