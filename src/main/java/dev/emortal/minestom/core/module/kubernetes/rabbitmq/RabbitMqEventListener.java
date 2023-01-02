package dev.emortal.minestom.core.module.kubernetes.rabbitmq;

import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.module.kubernetes.rabbitmq.types.ConnectEventDataPackage;
import dev.emortal.minestom.core.module.kubernetes.rabbitmq.types.DisconnectEventDataPackage;
import com.google.gson.Gson;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
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

    private static final String USERNAME = System.getenv("RABBITMQ_USERNAME");
    private static final String PASSWORD = System.getenv("RABBITMQ_PASSWORD");

    private static final Gson GSON = new Gson();

    private final Connection connection;
    private final Channel channel;

    public RabbitMqEventListener(EventNode<Event> eventNode) {
        if (USERNAME == null || PASSWORD == null) {
            LOGGER.warn("RabbitMQ username or password not set, skipping RabbitMQ event listener");
            this.connection = null;
            this.channel = null;
            return;
        }
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("rabbitmq-default");
        connectionFactory.setUsername(USERNAME);
        connectionFactory.setPassword(PASSWORD);

        try {
            this.connection = connectionFactory.newConnection();
            this.channel = this.connection.createChannel();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

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

    public void shutdown() {
        try {
            this.channel.close();
            this.connection.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
