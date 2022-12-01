package cc.towerdefence.minestom.module.kubernetes.rabbitmq.types;

import java.util.UUID;

public record ConnectEventDataPackage(UUID playerId, String username) {

}
