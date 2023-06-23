package dev.emortal.minestom.core.module.kubernetes.command.agones;

import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.core.utils.command.ExtraConditions;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentTime;
import org.jetbrains.annotations.NotNull;

public final class AgonesCommand extends Command {

    public AgonesCommand(@NotNull KubernetesModule kubernetesModule) {
        super("magones");
        setCondition(ExtraConditions.hasPermission("command.agones"));

        final SdkSubCommands sdkSubs = new SdkSubCommands(kubernetesModule.getSdk());

        final ArgumentLiteral get = new ArgumentLiteral("get");
        final ArgumentLiteral set = new ArgumentLiteral("set");
        final ArgumentString key = new ArgumentString("key");
        final ArgumentString metaValue = new ArgumentString("metaValue");

        addSyntax(sdkSubs::executeGetGameServer, get, new ArgumentLiteral("gameserver"));
        addSyntax(sdkSubs::executeReserve, new ArgumentLiteral("reserve"), new ArgumentTime("duration"));
        addSyntax(sdkSubs::executeAllocate, new ArgumentLiteral("allocate"));
        addSyntax(sdkSubs::executeSetAnnotation, set, new ArgumentLiteral("annotation"), key, metaValue);
        addSyntax(sdkSubs::executeSetLabel, set, new ArgumentLiteral("label"), key, metaValue);
        addSyntax(sdkSubs::executeShutdown, new ArgumentLiteral("shutdown"));
        addSyntax(sdkSubs::executeWatchGameserver, new ArgumentLiteral("watch"), new ArgumentLiteral("gameserver"));
    }

    public enum RequestStatus {

        NEXT(NamedTextColor.GREEN),
        ERROR(NamedTextColor.RED),
        COMPLETED(NamedTextColor.AQUA);

        private final NamedTextColor color;

        RequestStatus(NamedTextColor color) {
            this.color = color;
        }

        public @NotNull NamedTextColor getColor() {
            return this.color;
        }
    }
}
