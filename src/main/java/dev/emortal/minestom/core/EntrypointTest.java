package dev.emortal.minestom.core;

import dev.emortal.minestom.core.module.chat.ChatModule;
import dev.emortal.minestom.core.module.core.CoreModule;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.core.module.monitoring.MonitoringModule;
import dev.emortal.minestom.core.module.permissions.PermissionModule;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentEntity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.utils.entity.EntityFinder;

public final class EntrypointTest {

    public static void main(String[] args) {
        new MinestomServer.Builder()
                .address("localhost")
                .port(25565)
                .module(MonitoringModule.class, env -> new MonitoringModule(env, "core-test"))
                .module(KubernetesModule.class, KubernetesModule::new)
                .module(CoreModule.class, CoreModule::new)
                .module(PermissionModule.class, PermissionModule::new)
                .module(ChatModule.class, ChatModule::new)
                .build();

        Instance instance = MinecraftServer.getInstanceManager().createInstanceContainer();

        MinecraftServer.getGlobalEventHandler().addListener(PlayerLoginEvent.class, event -> {
            event.setSpawningInstance(instance);
        }).addListener(PlayerSpawnEvent.class, event -> {
            event.getPlayer().setGameMode(GameMode.CREATIVE);
            event.getPlayer().setFlying(true);
        });

        Command command = new Command("test");
        command.addSyntax((sender, context) -> {
            sender.sendMessage("Hello world!");
        }, new ArgumentWord("required"));
        command.addSyntax((sender, context) -> {
            sender.sendMessage("Hello world 2!");
        }, new ArgumentWord("required"), new ArgumentEntity("optional").onlyPlayers(true).setDefaultValue(EntityFinder::new));

        MinecraftServer.getCommandManager().register(command);
    }
}