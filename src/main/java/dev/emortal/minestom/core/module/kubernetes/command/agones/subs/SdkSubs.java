package dev.emortal.minestom.core.module.kubernetes.command.agones.subs;

import dev.emortal.minestom.core.module.kubernetes.command.agones.AgonesCommand;
import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import io.grpc.stub.StreamObserver;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.CommandContext;

import java.time.Duration;

public class SdkSubs {
    private final SDKGrpc.SDKStub sdk;

    public SdkSubs(SDKGrpc.SDKStub sdk) {
        this.sdk = sdk;
    }

    public void executeGetGameServer(CommandSender sender, CommandContext context) {
        this.sdk.getGameServer(AgonesSDKProto.Empty.getDefaultInstance(), new StreamObserver<>() {
            @Override
            public void onNext(AgonesSDKProto.GameServer value) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "GetGameServer", AgonesCommand.RequestStatus.NEXT,
                        value.toString()
                );

                sender.sendMessage(response);
            }

            @Override
            public void onError(Throwable t) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "GetGameServer", AgonesCommand.RequestStatus.ERROR,
                        t.getMessage()
                );

                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "GetGameServer", AgonesCommand.RequestStatus.COMPLETED,
                        ""
                );

                sender.sendMessage(response);
            }
        });
    }

    public void executeReserve(CommandSender sender, CommandContext context) {
        Duration duration = context.get("duration");
        this.sdk.reserve(AgonesSDKProto.Duration.newBuilder().setSeconds(duration.getSeconds()).build(), new StreamObserver<>() {
            @Override
            public void onNext(AgonesSDKProto.Empty value) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "Reserve", AgonesCommand.RequestStatus.NEXT,
                        "Reserved for %s seconds".formatted(duration.getSeconds())
                );

                sender.sendMessage(response);
            }

            @Override
            public void onError(Throwable t) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "Reserve", AgonesCommand.RequestStatus.ERROR,
                        t.getMessage()
                );

                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "Reserve", AgonesCommand.RequestStatus.COMPLETED,
                        ""
                );

                sender.sendMessage(response);
            }
        });
    }

    public void executeAllocate(CommandSender sender, CommandContext context) {
        this.sdk.allocate(AgonesSDKProto.Empty.getDefaultInstance(), new StreamObserver<>() {
            @Override
            public void onNext(AgonesSDKProto.Empty value) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "Allocate", AgonesCommand.RequestStatus.NEXT,
                        "Allocated"
                );

                sender.sendMessage(response);
            }

            @Override
            public void onError(Throwable t) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "Allocate", AgonesCommand.RequestStatus.ERROR,
                        t.getMessage()
                );

                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "Allocate", AgonesCommand.RequestStatus.COMPLETED,
                        ""
                );

                sender.sendMessage(response);
            }
        });
    }

    public void executeSetAnnotation(CommandSender sender, CommandContext context) {
        String key = context.get("key");
        String value = context.get("value");
        this.sdk.setAnnotation(AgonesSDKProto.KeyValue.newBuilder().setKey(key).setValue(value).build(), new StreamObserver<>() {
            @Override
            public void onNext(AgonesSDKProto.Empty value) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "SetAnnotation", AgonesCommand.RequestStatus.NEXT,
                        "Set annotation %s to %s".formatted(key, value)
                );

                sender.sendMessage(response);
            }

            @Override
            public void onError(Throwable t) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "SetAnnotation", AgonesCommand.RequestStatus.ERROR,
                        t.getMessage()
                );

                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "SetAnnotation", AgonesCommand.RequestStatus.COMPLETED,
                        ""
                );

                sender.sendMessage(response);
            }
        });
    }

    public void executeSetLabel(CommandSender commandSender, CommandContext context) {
        String key = context.get("key");
        String value = context.get("value");
        this.sdk.setLabel(AgonesSDKProto.KeyValue.newBuilder().setKey(key).setValue(value).build(), new StreamObserver<>() {
            @Override
            public void onNext(AgonesSDKProto.Empty value) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "SetLabel", AgonesCommand.RequestStatus.NEXT,
                        "Set label %s to %s".formatted(key, value)
                );

                commandSender.sendMessage(response);
            }

            @Override
            public void onError(Throwable t) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "SetLabel", AgonesCommand.RequestStatus.ERROR,
                        t.getMessage()
                );

                commandSender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "SetLabel", AgonesCommand.RequestStatus.COMPLETED,
                        ""
                );

                commandSender.sendMessage(response);
            }
        });
    }

    public void executeShutdown(CommandSender sender, CommandContext context) {
        this.sdk.shutdown(AgonesSDKProto.Empty.getDefaultInstance(), new StreamObserver<>() {
            @Override
            public void onNext(AgonesSDKProto.Empty value) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "Shutdown", AgonesCommand.RequestStatus.NEXT,
                        "Shutdown"
                );

                sender.sendMessage(response);
            }

            @Override
            public void onError(Throwable t) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "Shutdown", AgonesCommand.RequestStatus.ERROR,
                        t.getMessage()
                );

                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "Shutdown", AgonesCommand.RequestStatus.COMPLETED,
                        ""
                );

                sender.sendMessage(response);
            }
        });
    }

    public void executeWatchGameserver(CommandSender sender, CommandContext context) {
        this.sdk.watchGameServer(AgonesSDKProto.Empty.getDefaultInstance(), new StreamObserver<>() {
            @Override
            public void onNext(AgonesSDKProto.GameServer value) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "WatchGameServer", AgonesCommand.RequestStatus.NEXT,
                        value.toString()
                );

                sender.sendMessage(response);
            }

            @Override
            public void onError(Throwable t) {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "WatchGameServer", AgonesCommand.RequestStatus.ERROR,
                        t.getMessage()
                );

                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                Component response = AgonesCommand.generateMessage(
                        "SDK", "WatchGameServer", AgonesCommand.RequestStatus.COMPLETED,
                        ""
                );

                sender.sendMessage(response);
            }
        });
    }
}
