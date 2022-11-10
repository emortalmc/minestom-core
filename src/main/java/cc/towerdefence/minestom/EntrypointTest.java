package cc.towerdefence.minestom;

import cc.towerdefence.minestom.module.chat.ChatModule;
import cc.towerdefence.minestom.module.core.CoreModule;
import cc.towerdefence.minestom.module.kubernetes.KubernetesModule;
import cc.towerdefence.minestom.module.permissions.PermissionModule;

public final class EntrypointTest {

    public static void main(String[] args) {
        new MinestomServer.Builder()
                .address("localhost")
                .port(25565)
                .module(KubernetesModule.class, KubernetesModule::new)
                .module(CoreModule.class, CoreModule::new)
                .module(PermissionModule.class, PermissionModule::new)
                .module(ChatModule.class, ChatModule::new)
                .build();
    }
}