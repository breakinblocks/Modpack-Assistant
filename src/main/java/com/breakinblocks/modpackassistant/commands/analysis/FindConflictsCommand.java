package com.breakinblocks.modpackassistant.commands.analysis;

import com.breakinblocks.modpackassistant.analysis.RecipeConflictFinder;
import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.jobs.Run;
import com.breakinblocks.modpackassistant.jobs.RunScheduler;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

public final class FindConflictsCommand {
    private FindConflictsCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("findConflicts")
                .requires(MAPermissions.GAMEMASTER)
                .executes(context -> find(context.getSource(), null))
                .then(Commands.argument("type", IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.RECIPE_TYPE.keySet(), builder))
                        .executes(context -> find(context.getSource(), IdentifierArgument.getId(context, "type"))));
    }

    private static int find(CommandSourceStack source, @Nullable Identifier typeId) {
        RecipeType<?> filter = null;
        if (typeId != null) {
            filter = BuiltInRegistries.RECIPE_TYPE.getValue(typeId);
            if (filter == null) {
                return CommandResults.fail(source, Messages.UNKNOWN_RECIPE_TYPE.get(typeId));
            }
        }

        RecipeConflictFinder finder = new RecipeConflictFinder(source.getLevel());
        finder.prepare(source.getServer().getRecipeManager().getRecipes(), filter);
        ReportWriter.Context context = new ReportWriter.Context(source, "/ma findConflicts" + (typeId == null ? "" : " " + typeId))
                .note("recipe_count", finder.recipeCount())
                .note("bucket_count", finder.buckets().size())
                .note("skipped_dynamic", finder.skipped().size());

        Run run = new Run(source, "recipe conflict scan", source.getLevel().dimension());
        for (RecipeConflictFinder.Bucket bucket : finder.buckets()) {
            run.job(() -> finder.process(bucket));
        }
        run.onComplete(finished -> {
            finished.message(Messages.CONFLICTS_DONE.get(finder.conflictCount(), finder.duplicateCount(), finder.skipped().size()));
            ReportWriter.deliver(finished, ReportWriter.Family.RECIPES, "conflicts", "log", finder.log(context));
            ReportWriter.deliver(finished, ReportWriter.Family.RECIPES, "conflicts", "csv", finder.csv(context));
        });

        if (!RunScheduler.tryStart(run)) {
            return 0;
        }
        run.message(Messages.CONFLICTS_START.get(finder.recipeCount(), finder.buckets().size()));
        return Math.max(1, run.total());
    }
}
