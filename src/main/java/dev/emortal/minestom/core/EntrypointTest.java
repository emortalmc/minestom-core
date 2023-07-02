package dev.emortal.minestom.core;

import dev.emortal.minestom.core.module.monitoring.MonitoringModule;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentEntity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.utils.entity.EntityFinder;

public final class EntrypointTest {

    public static void main(String[] args) {
        MinestomServer.builder()
                .address("localhost")
                .port(25565)
                .mojangAuth(true)
                .commonModules()
                .module(MonitoringModule.class, MonitoringModule::new)
                .build();

        Instance instance = MinecraftServer.getInstanceManager().createInstanceContainer();

        var eventHandler = MinecraftServer.getGlobalEventHandler();
        eventHandler.addListener(PlayerLoginEvent.class, event -> event.setSpawningInstance(instance));
        eventHandler.addListener(PlayerSpawnEvent.class, event -> {
            final Player player = event.getPlayer();
            player.setGameMode(GameMode.CREATIVE);
            player.setFlying(true);
        });

        var command = new Command("test");

        var required = new ArgumentWord("required");
        var optional = new ArgumentEntity("optional").onlyPlayers(true).setDefaultValue(EntityFinder::new);

        command.addSyntax((sender, context) -> sender.sendMessage("Hello world!"), required);
        command.addSyntax((sender, context) -> sender.sendMessage("Hello world 2!"), required, optional);

        var testPermsCmd = new Command("testperms");
        testPermsCmd.setCondition((sender, commandName) -> sender.hasPermission("command.testperms"));
        testPermsCmd.setDefaultExecutor((sender, context) -> sender.sendMessage("works :)"));

        var commandManager = MinecraftServer.getCommandManager();
        commandManager.register(command);
        commandManager.register(testPermsCmd);
    }
}
