package com.breakinblocks.modpackassistant.commands.args;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.StringRepresentableArgument;
import net.minecraft.util.StringRepresentable;

public final class ReportFormatArgument extends StringRepresentableArgument<ReportFormatArgument.ReportFormat> {
    public enum ReportFormat implements StringRepresentable {
        JSON("json"),
        CSV("csv");

        public static final Codec<ReportFormat> CODEC = StringRepresentable.fromEnum(ReportFormat::values);

        private final String name;

        ReportFormat(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public ReportFormatArgument() {
        super(ReportFormat.CODEC, ReportFormat::values);
    }

    public static ReportFormatArgument reportFormat() {
        return new ReportFormatArgument();
    }

    public static ReportFormat get(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, ReportFormat.class);
    }
}
