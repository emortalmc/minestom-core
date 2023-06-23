package dev.emortal.minestom.core.module.kubernetes.command.agones;

import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import io.grpc.stub.StreamObserver;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.CommandContext;

import java.time.Duration;
import org.jetbrains.annotations.NotNull;

public final class SdkSubCommands {

    private static Component generateMessage(String sdk, String method, AgonesCommand.RequestStatus status, String message) {
        final String text = "Agones >> [%s.%s] (%s) %s".formatted(sdk, method, status.name(), message);
        return Component.text(text, status.getColor());
    };

    private final SDKGrpc.SDKStub sdk;

    public SdkSubCommands(@NotNull SDKGrpc.SDKStub sdk) {
        this.sdk = sdk;
    }

    private <T> StreamObserver<T> createCallback(CommandSender sender, String sdkMethod, Function<T, String> nextMessageProvider) {
        return new StreamObserver<T>() {
            @Override
            public void onNext(@NotNull T value) {
                final String nextMessage = nextMessageProvider.apply(value);
                final Component response = generateMessage("SDK", sdkMethod, AgonesCommand.RequestStatus.NEXT, nextMessage);
                sender.sendMessage(response);
            }

            @Override
            public void onError(@NotNull Throwable exception) {
                final Component response = generateMessage("SDK", sdkMethod, AgonesCommand.RequestStatus.ERROR, exception.getMessage());
                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                final Component response = generateMessage("SDK", sdkMethod, AgonesCommand.RequestStatus.COMPLETED, "");
                sender.sendMessage(response);
            }
        };
    }

    public void executeGetGameServer(@NotNull CommandSender sender, @NotNull CommandContext context) {
        sdk.getGameServer(AgonesSDKProto.Empty.getDefaultInstance(), createCallback(sender, "GetGameServer", Object::toString));
    }

    public void executeReserve(@NotNull CommandSender sender, @NotNull CommandContext context) {
        final Duration duration = context.get("duration");

        sdk.reserve(AgonesSDKProto.Duration.newBuilder().setSeconds(duration.getSeconds()).build(), createCallback(sender, "Reserve",
                v -> "Reserved for %s seconds".formatted(duration.getSeconds())));
    }

    public void executeAllocate(@NotNull CommandSender sender, @NotNull CommandContext context) {
        sdk.allocate(AgonesSDKProto.Empty.getDefaultInstance(), createCallback(sender, "Allocate", v -> "Allocated"));
    }

    public void executeSetAnnotation(@NotNull CommandSender sender, @NotNull CommandContext context) {
        final String key = context.get("key");
        final String value = context.get("metaValue");

        final var keyValue = AgonesSDKProto.KeyValue.newBuilder().setKey(key).setValue(value).build();
        sdk.setAnnotation(keyValue, createCallback(sender, "SetAnnotation", v -> "Set annotation %s to %s".formatted(key, v)));
    }

    public void executeSetLabel(@NotNull CommandSender sender, @NotNull CommandContext context) {
        final String key = context.get("key");
        final String value = context.get("metaValue");

        final var keyValue = AgonesSDKProto.KeyValue.newBuilder().setKey(key).setValue(value).build();
        sdk.setLabel(keyValue, createCallback(sender, "SetLabel", v -> "Set label %s to %s".formatted(key, v)));
    }

    public void executeShutdown(@NotNull CommandSender sender, @NotNull CommandContext context) {
        sdk.shutdown(AgonesSDKProto.Empty.getDefaultInstance(), createCallback(sender, "Shutdown", v -> "Shutdown"));
    }

    public void executeWatchGameserver(@NotNull CommandSender sender, @NotNull CommandContext context) {
        sdk.watchGameServer(AgonesSDKProto.Empty.getDefaultInstance(), createCallback(sender, "WatchGameServer", Object::toString));
    }
}
