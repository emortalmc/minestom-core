package dev.emortal.minestom.core;

import dev.emortal.minestom.core.module.chat.ChatModule;
import dev.emortal.minestom.core.module.core.CoreModule;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.core.module.permissions.PermissionModule;

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