package com.breakinblocks.modpackassistant.datagen;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = ModpackAssistant.MOD_ID)
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
