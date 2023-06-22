package dev.emortal.minestom.core.module.kubernetes.command.agones;

import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import io.grpc.stub.StreamObserver;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.CommandContext;

import java.time.Duration;
import org.jetbrains.annotations.NotNull;

public final class SdkSubCommands {

    private final SDKGrpc.SDKStub sdk;

    public SdkSubCommands(@NotNull SDKGrpc.SDKStub sdk) {
        this.sdk = sdk;
    }

    public void executeGetGameServer(@NotNull CommandSender sender, @NotNull CommandContext context) {
        sdk.getGameServer(AgonesSDKProto.Empty.getDefaultInstance(), new StreamObserver<>() {
            @Override
            public void onNext(@NotNull AgonesSDKProto.GameServer value) {
                final Component response = AgonesCommand.generateMessage("SDK", "GetGameServer", AgonesCommand.RequestStatus.NEXT, value.toString());
                sender.sendMessage(response);
            }

            @Override
            public void onError(@NotNull Throwable exception) {
                final Component response = AgonesCommand.generateMessage("SDK", "GetGameServer", AgonesCommand.RequestStatus.ERROR, exception.getMessage());
                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                final Component response = AgonesCommand.generateMessage("SDK", "GetGameServer", AgonesCommand.RequestStatus.COMPLETED, "");
                sender.sendMessage(response);
            }
        });
    }

    public void executeReserve(@NotNull CommandSender sender, @NotNull CommandContext context) {
        final Duration duration = context.get("duration");

        sdk.reserve(AgonesSDKProto.Duration.newBuilder().setSeconds(duration.getSeconds()).build(), new StreamObserver<>() {
            @Override
            public void onNext(AgonesSDKProto.Empty value) {
                final Component response = AgonesCommand.generateMessage("SDK", "Reserve", AgonesCommand.RequestStatus.NEXT,
                        "Reserved for %s seconds".formatted(duration.getSeconds()));
                sender.sendMessage(response);
            }

            @Override
            public void onError(@NotNull Throwable exception) {
                final Component response = AgonesCommand.generateMessage("SDK", "Reserve", AgonesCommand.RequestStatus.ERROR, exception.getMessage());
                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                final Component response = AgonesCommand.generateMessage("SDK", "Reserve", AgonesCommand.RequestStatus.COMPLETED, "");
                sender.sendMessage(response);
            }
        });
    }

    public void executeAllocate(@NotNull CommandSender sender, @NotNull CommandContext context) {
        sdk.allocate(AgonesSDKProto.Empty.getDefaultInstance(), new StreamObserver<>() {
            @Override
            public void onNext(@NotNull AgonesSDKProto.Empty value) {
                final Component response = AgonesCommand.generateMessage("SDK", "Allocate", AgonesCommand.RequestStatus.NEXT, "Allocated");
                sender.sendMessage(response);
            }

            @Override
            public void onError(@NotNull Throwable exception) {
                final Component response = AgonesCommand.generateMessage("SDK", "Allocate", AgonesCommand.RequestStatus.ERROR, exception.getMessage());
                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                final Component response = AgonesCommand.generateMessage("SDK", "Allocate", AgonesCommand.RequestStatus.COMPLETED, "");
                sender.sendMessage(response);
            }
        });
    }

    public void executeSetAnnotation(@NotNull CommandSender sender, @NotNull CommandContext context) {
        final String key = context.get("key");
        final String value = context.get("metaValue");

        sdk.setAnnotation(AgonesSDKProto.KeyValue.newBuilder().setKey(key).setValue(value).build(), new StreamObserver<>() {
            @Override
            public void onNext(@NotNull AgonesSDKProto.Empty value) {
                final Component response = AgonesCommand.generateMessage("SDK", "SetAnnotation", AgonesCommand.RequestStatus.NEXT,
                        "Set annotation %s to %s".formatted(key, value));
                sender.sendMessage(response);
            }

            @Override
            public void onError(@NotNull Throwable exception) {
                final Component response = AgonesCommand.generateMessage("SDK", "SetAnnotation", AgonesCommand.RequestStatus.ERROR, exception.getMessage());
                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                final Component response = AgonesCommand.generateMessage("SDK", "SetAnnotation", AgonesCommand.RequestStatus.COMPLETED, "");
                sender.sendMessage(response);
            }
        });
    }

    public void executeSetLabel(@NotNull CommandSender commandSender, @NotNull CommandContext context) {
        final String key = context.get("key");
        final String value = context.get("metaValue");

        sdk.setLabel(AgonesSDKProto.KeyValue.newBuilder().setKey(key).setValue(value).build(), new StreamObserver<>() {
            @Override
            public void onNext(@NotNull AgonesSDKProto.Empty value) {
                final Component response = AgonesCommand.generateMessage("SDK", "SetLabel", AgonesCommand.RequestStatus.NEXT,
                        "Set label %s to %s".formatted(key, value));
                commandSender.sendMessage(response);
            }

            @Override
            public void onError(@NotNull Throwable exception) {
                final Component response = AgonesCommand.generateMessage("SDK", "SetLabel", AgonesCommand.RequestStatus.ERROR, exception.getMessage());
                commandSender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                final Component response = AgonesCommand.generateMessage("SDK", "SetLabel", AgonesCommand.RequestStatus.COMPLETED, "");
                commandSender.sendMessage(response);
            }
        });
    }

    public void executeShutdown(@NotNull CommandSender sender, @NotNull CommandContext context) {
        sdk.shutdown(AgonesSDKProto.Empty.getDefaultInstance(), new StreamObserver<>() {
            @Override
            public void onNext(@NotNull AgonesSDKProto.Empty value) {
                final Component response = AgonesCommand.generateMessage("SDK", "Shutdown", AgonesCommand.RequestStatus.NEXT, "Shutdown");
                sender.sendMessage(response);
            }

            @Override
            public void onError(@NotNull Throwable exception) {
                final Component response = AgonesCommand.generateMessage("SDK", "Shutdown", AgonesCommand.RequestStatus.ERROR, exception.getMessage());
                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                final Component response = AgonesCommand.generateMessage("SDK", "Shutdown", AgonesCommand.RequestStatus.COMPLETED, "");
                sender.sendMessage(response);
            }
        });
    }

    public void executeWatchGameserver(@NotNull CommandSender sender, @NotNull CommandContext context) {
        sdk.watchGameServer(AgonesSDKProto.Empty.getDefaultInstance(), new StreamObserver<>() {
            @Override
            public void onNext(@NotNull AgonesSDKProto.GameServer value) {
                final Component response = AgonesCommand.generateMessage("SDK", "WatchGameServer", AgonesCommand.RequestStatus.NEXT, value.toString());
                sender.sendMessage(response);
            }

            @Override
            public void onError(@NotNull Throwable exception) {
                final Component response = AgonesCommand.generateMessage("SDK", "WatchGameServer", AgonesCommand.RequestStatus.ERROR, exception.getMessage());
                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                final Component response = AgonesCommand.generateMessage("SDK", "WatchGameServer", AgonesCommand.RequestStatus.COMPLETED, "");
                sender.sendMessage(response);
            }
        });
    }
}
