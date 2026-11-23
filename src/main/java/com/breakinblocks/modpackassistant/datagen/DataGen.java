package com.breakinblocks.modpackassistant.datagen;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = ModpackAssistant.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DataGen {
    private DataGen() {
    }

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        generator.addProvider(event.includeClient(), new LangProvider(output));
        generator.addProvider(event.includeServer(), new EntityTagProvider(output, event.getLookupProvider(), event.getExistingFileHelper()));
    }
}
