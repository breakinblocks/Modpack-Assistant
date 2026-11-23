package com.breakinblocks.modpackassistant;

import com.breakinblocks.modpackassistant.client.showoff.ShowoffClient;
import com.breakinblocks.modpackassistant.config.MAClientConfig;
import com.breakinblocks.modpackassistant.config.MAConfig;
import com.breakinblocks.modpackassistant.net.MANetworking;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(ModpackAssistant.MOD_ID)
public class ModpackAssistant {
    public static final String MOD_ID = "modpackassistant";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public ModpackAssistant() {
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext context = ModLoadingContext.get();
        context.registerConfig(ModConfig.Type.COMMON, MAConfig.SPEC);
        MANetworking.register();
        if (FMLEnvironment.dist.isClient()) {
            context.registerConfig(ModConfig.Type.CLIENT, MAClientConfig.SPEC);
            ShowoffClient.init(eventBus);
        }
    }
}
