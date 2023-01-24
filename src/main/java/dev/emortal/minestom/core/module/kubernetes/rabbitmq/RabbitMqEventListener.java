package dev.emortal.minestom.core.module.kubernetes.rabbitmq;

import com.google.protobuf.AbstractMessage;
import dev.emortal.api.utils.parser.ProtoParserRegistry;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.module.kubernetes.rabbitmq.types.ConnectEventDataPackage;
import dev.emortal.minestom.core.module.kubernetes.rabbitmq.types.DisconnectEventDataPackage;
import com.google.gson.Gson;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class RabbitMqEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqEventListener.class);

    private static final String CONNECTIONS_EXCHANGE = "mc:connections";
    private static final String BACKEND_ALL_EXCHANGE = "mc:gameserver:all";

    private static final String HOST = System.getenv("RABBITMQ_HOST");
    private static final String USERNAME = System.getenv("RABBITMQ_USERNAME");
    private static final String PASSWORD = System.getenv("RABBITMQ_PASSWORD");

    private static final Gson GSON = new Gson();

    private final Map<Class<?>, Consumer<AbstractMessage>> protoListeners = new ConcurrentHashMap<>();
    private final Connection connection;
    private final Channel channel;
    private final String selfQueueName;

    public RabbitMqEventListener(EventNode<Event> eventNode) {
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

        eventNode.addListener(PlayerLoginEvent.class, this::onPlayerLogin)
                .addListener(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        MinecraftServer.getSchedulerManager().buildShutdownTask(this::shutdown);
    }

    public void onPlayerLogin(PlayerLoginEvent event) {
        ConnectEventDataPackage dataPackage = new ConnectEventDataPackage(event.getPlayer().getUuid(), event.getPlayer().getUsername());
        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
                .timestamp(new Date())
                .type("connect")
                .appId(Environment.getHostname())
                .build();

        try {
            this.channel.basicPublish(CONNECTIONS_EXCHANGE, "", basicProperties, GSON.toJson(dataPackage).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        DisconnectEventDataPackage dataPackage = new DisconnectEventDataPackage(event.getPlayer().getUuid());
        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
                .timestamp(new Date())
                .type("disconnect")
                .appId(Environment.getHostname())
                .build();

        try {
            this.channel.basicPublish(CONNECTIONS_EXCHANGE, "", basicProperties, GSON.toJson(dataPackage).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends AbstractMessage> void addListener(final Class<T> messageType, final Consumer<AbstractMessage> listener) {
        this.protoListeners.put(messageType, listener);
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
