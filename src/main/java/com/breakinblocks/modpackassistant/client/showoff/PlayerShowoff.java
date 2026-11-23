package com.breakinblocks.modpackassistant.client.showoff;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

final class PlayerShowoff {
    static final int PARTS = 6;
    static final String[] PART_NAMES = {"Head", "Body", "Left Arm", "Right Arm", "Left Leg", "Right Leg"};
    private static final String[] POSE_KEYS = {"Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg"};
    private static final List<EquipmentSlot> EQUIPMENT = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
    private static final long SKIN_TIMEOUT_SECONDS = 30;

    private final ShowoffPlayer player;
    private final @Nullable Tag profileTag;
    private final float[][] rotations = new float[PARTS][3];
    private long request;
    private int revision;
    private String status = "Enter a username or UUID";
    private boolean closed;

    PlayerShowoff(ClientLevel level, CompoundTag entityTag) {
        Objects.requireNonNull(level, "Client level");
        Objects.requireNonNull(entityTag, "Mannequin NBT");
        player = new ShowoffPlayer(level, rotations);
        profileTag = entityTag.get("profile");
        loadEquipment(level, entityTag);
        loadPose(entityTag);
    }

    CompletableFuture<Void> prepareSkin() {
        if (profileTag == null) {
            return CompletableFuture.completedFuture(null);
        }
        GameProfile profile = profile(profileTag);
        Minecraft minecraft = Minecraft.getInstance();
        return resolve(profile)
                .thenComposeAsync(gameProfile -> {
                    if (!gameProfile.getProperties().containsKey("textures")) {
                        throw new IllegalStateException("Player skin could not be loaded for " + gameProfile.getName());
                    }
                    return ShowoffSkin.load(gameProfile);
                }, minecraft)
                .orTimeout(SKIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .thenAcceptAsync(skin -> {
                    ensureOpen();
                    skin(skin);
                }, minecraft);
    }

    String status() {
        return status;
    }

    int revision() {
        return revision;
    }

    void close() {
        closed = true;
        request++;
    }

    void inputChanged() {
        request++;
        skin(ShowoffSkin.DEFAULT);
        status = "Enter a username or UUID";
    }

    float rotation(int part, int axis) {
        return rotations[Objects.checkIndex(part, PARTS)][Objects.checkIndex(axis, 3)];
    }

    void rotation(int part, int axis, float value) {
        if (!Float.isFinite(value) || value < -180.0F || value > 180.0F) {
            throw new IllegalArgumentException("Limb angles must be finite and between -180 and 180 degrees");
        }
        rotations[Objects.checkIndex(part, PARTS)][Objects.checkIndex(axis, 3)] = value;
        revision++;
    }

    void reset() {
        for (float[] part : rotations) {
            Arrays.fill(part, 0.0F);
        }
        revision++;
    }

    ItemStack equipment(EquipmentSlot slot) {
        Objects.requireNonNull(slot, "Equipment slot");
        ensureOpen();
        return player.getItemBySlot(slot).copy();
    }

    void equip(EquipmentSlot slot, ItemStack stack) {
        Objects.requireNonNull(slot, "Equipment slot");
        Objects.requireNonNull(stack, "Equipment stack");
        ensureOpen();
        player.setItemSlot(slot, stack.copy());
        revision++;
    }

    boolean fits(EquipmentSlot slot, ItemStack stack) {
        return slot.getType() == EquipmentSlot.Type.HAND || LivingEntity.getEquipmentSlotForItem(stack) == slot;
    }

    void lookup(String input) {
        if (closed) {
            throw new IllegalStateException("The player showoff session is closed");
        }
        String value = input.trim();
        long serial = ++request;
        if (value.isEmpty()) {
            status = "Enter a username or UUID";
            return;
        }
        boolean isUuid = value.matches("[0-9a-fA-F]{32}")
                || value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        if (!isUuid && !value.matches("[A-Za-z0-9_]{1,16}")) {
            status = "Invalid username or UUID";
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        skin(ShowoffSkin.DEFAULT);
        status = "Loading skin...";
        CompletableFuture<Optional<GameProfile>> profileFuture = isUuid
                ? fetch(parseUuid(value))
                : fetch(value);
        profileFuture.thenComposeAsync(found -> {
            if (closed || serial != request) {
                throw new IllegalStateException("Player skin request was superseded");
            }
            GameProfile profile = found.orElseThrow(() -> new IllegalArgumentException("Player profile not found: " + value));
            skin(ShowoffSkin.fallback(profile));
            return ShowoffSkin.load(profile);
        }, minecraft).orTimeout(SKIN_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenCompleteAsync((skin, error) -> {
            if (closed || serial != request) {
                return;
            }
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                status = "Skin lookup failed: " + cause.getMessage();
                ModpackAssistant.LOGGER.warn("Could not resolve player showoff skin for {}", value, cause);
            } else {
                skin(skin);
                status = "Skin loaded: " + value;
            }
            revision++;
        }, minecraft);
    }

    void render(PoseStack poseStack, MultiBufferSource buffers) {
        ShowoffPlayerRenderer renderer = ShowoffClient.playerRenderer(player.skin().slim());
        renderer.render(player, 0.0F, 1.0F, poseStack, buffers, LightTexture.FULL_BRIGHT);
    }

    private void skin(ShowoffSkin skin) {
        player.skin(skin);
        revision++;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("The player showoff session is closed");
        }
    }

    private void loadEquipment(ClientLevel level, CompoundTag entityTag) {
        if (!entityTag.contains("equipment")) {
            return;
        }
        if (!(entityTag.get("equipment") instanceof CompoundTag equipment)) {
            throw new IllegalArgumentException("Mannequin equipment must be a compound");
        }
        for (String key : equipment.getAllKeys()) {
            EquipmentSlot slot = EQUIPMENT.stream().filter(candidate -> candidate.getName().equals(key)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown mannequin equipment slot: " + key));
            if (!(equipment.get(key) instanceof CompoundTag itemTag)) {
                throw new IllegalArgumentException("Mannequin equipment." + key + " must be a compound");
            }
            ItemStack stack = ItemStack.of(itemTag);
            if (stack.isEmpty() && !itemTag.isEmpty()) {
                throw new IllegalArgumentException("Invalid mannequin equipment NBT in " + key);
            }
            player.setItemSlot(slot, stack);
        }
    }

    private void loadPose(CompoundTag entityTag) {
        if (!entityTag.contains("Pose")) {
            return;
        }
        if (!(entityTag.get("Pose") instanceof CompoundTag pose)) {
            throw new IllegalArgumentException("Showoff Pose must be a compound");
        }
        for (String key : pose.getAllKeys()) {
            if (!Arrays.asList(POSE_KEYS).contains(key)) {
                throw new IllegalArgumentException("Unknown showoff Pose limb: " + key);
            }
        }
        for (int part = 0; part < PARTS; part++) {
            String name = POSE_KEYS[part];
            Tag raw = pose.get(name);
            if (raw == null) {
                continue;
            }
            if (!(raw instanceof ListTag angles) || angles.size() != 3) {
                throw new IllegalArgumentException("Showoff Pose." + name + " must be a numeric list of three angles");
            }
            float[] values = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                if (!(angles.get(axis) instanceof NumericTag numeric)) {
                    throw new IllegalArgumentException("Showoff Pose." + name + " contains invalid angles");
                }
                values[axis] = numeric.getAsFloat();
                if (!Float.isFinite(values[axis]) || values[axis] < -180.0F || values[axis] > 180.0F) {
                    throw new IllegalArgumentException("Showoff Pose." + name + " contains invalid angles");
                }
            }
            System.arraycopy(values, 0, rotations[part], 0, 3);
        }
        revision++;
    }

    private static GameProfile profile(Tag tag) {
        if (tag.getId() == Tag.TAG_STRING) {
            String name = tag.getAsString();
            if (!name.matches("[A-Za-z0-9_]{1,16}")) {
                throw new IllegalArgumentException("Invalid mannequin profile name: " + name);
            }
            return new GameProfile(null, name);
        }
        if (!(tag instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("Invalid mannequin profile NBT");
        }
        CompoundTag normalized = compound.copy();
        if (normalized.contains("name", Tag.TAG_STRING) && !normalized.contains("Name")) {
            normalized.putString("Name", normalized.getString("name"));
        }
        if (normalized.hasUUID("id") && !normalized.hasUUID("Id")) {
            normalized.putUUID("Id", normalized.getUUID("id"));
        }
        GameProfile profile = NbtUtils.readGameProfile(normalized);
        if (profile == null || (profile.getId() == null && (profile.getName() == null || profile.getName().isEmpty()))) {
            throw new IllegalArgumentException("Mannequin profile needs a name or id");
        }
        return profile;
    }

    private static CompletableFuture<GameProfile> resolve(GameProfile profile) {
        if (profile.getProperties().containsKey("textures")) {
            return CompletableFuture.completedFuture(profile);
        }
        CompletableFuture<Optional<GameProfile>> found = profile.getId() != null
                ? fetch(profile.getId())
                : fetch(profile.getName());
        return found.thenApply(result -> result.orElseThrow(() -> new IllegalStateException(
                "Player profile not found: " + (profile.getName() != null ? profile.getName()
                        : profile.getId() != null ? profile.getId().toString() : ""))));
    }

    private static CompletableFuture<Optional<GameProfile>> fetch(String name) {
        CompletableFuture<Optional<GameProfile>> result = new CompletableFuture<>();
        SkullBlockEntity.updateGameprofile(new GameProfile(null, name), profile -> result.complete(
                profile != null && profile.getId() != null ? Optional.of(profile) : Optional.empty()));
        return result;
    }

    private static CompletableFuture<Optional<GameProfile>> fetch(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            GameProfile profile = Minecraft.getInstance().getMinecraftSessionService().fillProfileProperties(new GameProfile(id, null), true);
            return profile != null && profile.getName() != null ? Optional.of(profile) : Optional.empty();
        }, Util.backgroundExecutor());
    }

    private static UUID parseUuid(String value) {
        String compact = value.replace("-", "");
        return UUID.fromString(compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-"
                + compact.substring(12, 16) + "-" + compact.substring(16, 20) + "-" + compact.substring(20));
    }
}
