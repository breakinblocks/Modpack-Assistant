package com.breakinblocks.modpackassistant.net;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.breakinblocks.modpackassistant.client.barriers.BarrierDisplay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShowBarriersPayload(boolean shown) implements CustomPacketPayload {
    public static final Type<ShowBarriersPayload> TYPE = new Type<>(ModpackAssistant.id("show_barriers"));
    public static final StreamCodec<ByteBuf, ShowBarriersPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(ShowBarriersPayload::new, ShowBarriersPayload::shown);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShowBarriersPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            context.enqueueWork(() -> BarrierDisplay.set(payload.shown()));
        }
    }
}
