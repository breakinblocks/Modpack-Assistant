package com.breakinblocks.modpackassistant;

import com.breakinblocks.modpackassistant.commands.args.MAArguments;
import com.breakinblocks.modpackassistant.config.MAConfig;
import com.breakinblocks.modpackassistant.gametest.MAGameTests;
import com.breakinblocks.modpackassistant.jobs.ChunkAccessor;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(ModpackAssistant.MOD_ID)
public class ModpackAssistant {
    public static final String MOD_ID = "modpackassistant";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public ModpackAssistant(IEventBus eventBus, ModContainer container, Dist dist) {
        MAArguments.ARGUMENT_TYPES.register(eventBus);
        ChunkAccessor.TICKET_TYPES.register(eventBus);
        MAGameTests.TEST_INSTANCE_TYPES.register(eventBus);
        container.registerConfig(ModConfig.Type.COMMON, MAConfig.SPEC);
    }
}
