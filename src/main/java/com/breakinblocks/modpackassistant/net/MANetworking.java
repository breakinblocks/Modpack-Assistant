package com.breakinblocks.modpackassistant.net;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = ModpackAssistant.MOD_ID)
public final class MANetworking {
    private MANetworking() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(ModpackAssistant.MOD_ID).optional();
        registrar.playToClient(SetClipboardPayload.TYPE, SetClipboardPayload.STREAM_CODEC, SetClipboardPayload::handle);
    }

    public static boolean sendClipboard(ServerPlayer player, String text) {
        if (!player.connection.hasChannel(SetClipboardPayload.TYPE)) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new SetClipboardPayload(text));
        return true;
    }
}
