package dev.emortal.minestom.core;

import dev.emortal.minestom.core.module.monitoring.MonitoringModule;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentEntity;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.entity.EntityFinder;

public final class EntrypointTest {

    public static void main(String[] args) {
        MinestomServer.builder()
                .address("localhost")
                .port(25565)
                .mojangAuth(true)
                .commonModules()
                .module(MonitoringModule.class, MonitoringModule::new)
                .buildAndStart();

        Instance instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(-20, 0, Block.GRASS_BLOCK));

        GlobalEventHandler eventHandler = MinecraftServer.getGlobalEventHandler();
        eventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(instance);
            event.getPlayer().setRespawnPoint(new Pos(0, 0, 0));
        });
        eventHandler.addListener(PlayerSpawnEvent.class, event -> {
            final Player player = event.getPlayer();
            player.setGameMode(GameMode.CREATIVE);
            player.setFlying(true);
        });

        Command command = new Command("test");

        ArgumentWord required = new ArgumentWord("required");
        Argument<EntityFinder> optional = new ArgumentEntity("optional").onlyPlayers(true).setDefaultValue(EntityFinder::new);

        command.addSyntax((sender, context) -> sender.sendMessage("Hello world!"), required);
        command.addSyntax((sender, context) -> sender.sendMessage("Hello world 2!"), required, optional);

        Command testPermsCmd = new Command("testperms");
        testPermsCmd.setCondition((sender, commandName) -> sender.hasPermission("command.testperms"));
        testPermsCmd.setDefaultExecutor((sender, context) -> sender.sendMessage("works :)"));

        CommandManager commandManager = MinecraftServer.getCommandManager();
        commandManager.register(command);
        commandManager.register(testPermsCmd);
    }
}
