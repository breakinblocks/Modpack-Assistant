package com.breakinblocks.modpackassistant.commands.args;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class MAArguments {
    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES = DeferredRegister.create(BuiltInRegistries.COMMAND_ARGUMENT_TYPE, ModpackAssistant.MOD_ID);

    public static final DeferredHolder<ArgumentTypeInfo<?, ?>, SingletonArgumentInfo<KillTypeArgument>> KILL_TYPE = singleton("kill_type", KillTypeArgument.class, KillTypeArgument::new);
    public static final DeferredHolder<ArgumentTypeInfo<?, ?>, SingletonArgumentInfo<HarvestModeArgument>> HARVEST_MODE = singleton("harvest_mode", HarvestModeArgument.class, HarvestModeArgument::new);
    public static final DeferredHolder<ArgumentTypeInfo<?, ?>, SingletonArgumentInfo<RegistryKindArgument>> REGISTRY_KIND = singleton("registry_kind", RegistryKindArgument.class, RegistryKindArgument::new);
    public static final DeferredHolder<ArgumentTypeInfo<?, ?>, SingletonArgumentInfo<ReportFormatArgument>> REPORT_FORMAT = singleton("report_format", ReportFormatArgument.class, ReportFormatArgument::new);
    public static final DeferredHolder<ArgumentTypeInfo<?, ?>, SingletonArgumentInfo<ClearKeepArgument>> CLEAR_KEEP = singleton("clear_keep", ClearKeepArgument.class, ClearKeepArgument::new);

    private MAArguments() {
    }

    private static <A extends ArgumentType<?>> DeferredHolder<ArgumentTypeInfo<?, ?>, SingletonArgumentInfo<A>> singleton(String name, Class<A> type, Supplier<A> factory) {
        return ARGUMENT_TYPES.register(name, () -> ArgumentTypeInfos.registerByClass(type, SingletonArgumentInfo.contextFree(factory)));
    }
}
