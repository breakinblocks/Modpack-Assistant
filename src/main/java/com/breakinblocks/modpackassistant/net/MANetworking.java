package com.breakinblocks.modpackassistant.net;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Function;

public final class MANetworking {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named(ModpackAssistant.id("main"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .simpleChannel();
    private static int nextId;

    private MANetworking() {
    }

    public static void register() {
        register(SetClipboardPayload.class, SetClipboardPayload::read);
        register(ShowoffOpenPayload.class, ShowoffOpenPayload::read);
        register(ShowoffViewPayload.class, ShowoffViewPayload::read);
        register(ShowoffBackgroundPayload.class, ShowoffBackgroundPayload::read);
        register(ShowoffScreenshotPayload.class, ShowoffScreenshotPayload::read);
        register(ShowoffClosePayload.class, buffer -> ShowoffClosePayload.INSTANCE);
        register(ShowBarriersPayload.class, ShowBarriersPayload::read);
    }

    private static <T extends MAPayload> void register(Class<T> type, Function<FriendlyByteBuf, T> reader) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MAPayload::write)
                .decoder(reader)
                .consumerMainThread((payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        payload.handle();
                    }
                })
                .add();
    }

    public static boolean sendClipboard(ServerPlayer player, String text) {
        if (text.length() > SetClipboardPayload.MAX_TEXT_LENGTH) {
            return false;
        }
        return send(player, new SetClipboardPayload(text));
    }

    public static boolean canShowoff(ServerPlayer player) {
        return hasChannel(player);
    }

    public static boolean sendShowoff(ServerPlayer player, MAPayload payload) {
        return send(player, payload);
    }

    public static boolean sendShowBarriers(ServerPlayer player, boolean shown) {
        return send(player, new ShowBarriersPayload(shown));
    }

    private static boolean send(ServerPlayer player, MAPayload payload) {
        if (!hasChannel(player)) {
            return false;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        return true;
    }

    private static boolean hasChannel(ServerPlayer player) {
        return !(player instanceof FakePlayer) && player.connection != null
                && CHANNEL.isRemotePresent(player.connection.connection);
    }
}
