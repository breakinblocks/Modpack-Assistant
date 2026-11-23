package com.breakinblocks.modpackassistant.net;

import com.breakinblocks.modpackassistant.client.showoff.ShowoffClient;
import com.breakinblocks.modpackassistant.showoff.ShowoffSubject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ShowoffOpenPayload(ShowoffSubject subject, ResourceLocation id, CompoundTag data) implements MAPayload {
    public static final String PLAYER_INPUT_KEY = "modpackassistant_player";
    public static final ResourceLocation PLAYER_ID = new ResourceLocation("mannequin");

    public static ShowoffOpenPayload read(FriendlyByteBuf buffer) {
        ShowoffSubject subject = ShowoffSubject.read(buffer);
        ResourceLocation id = buffer.readResourceLocation();
        CompoundTag data = buffer.readNbt();
        return new ShowoffOpenPayload(subject, id, Objects.requireNonNullElseGet(data, CompoundTag::new));
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        subject.write(buffer);
        buffer.writeResourceLocation(id);
        buffer.writeNbt(data);
    }

    @Override
    public void handle() {
        ShowoffClient.open(subject, id, data);
    }
}
