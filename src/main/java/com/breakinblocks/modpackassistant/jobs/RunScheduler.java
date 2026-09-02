package com.breakinblocks.modpackassistant.jobs;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.breakinblocks.modpackassistant.config.MAConfig;
import com.breakinblocks.modpackassistant.util.Messages;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@EventBusSubscriber(modid = ModpackAssistant.MOD_ID)
public final class RunScheduler {
    @Nullable
    private static Run active;
    private static int tickCounter;

    private RunScheduler() {
    }

    public static Optional<Run> active() {
        return Optional.ofNullable(active);
    }

    public static boolean isBusy() {
        return active != null;
    }

    public static boolean tryStart(Run run) {
        Run current = active;
        if (current != null) {
            run.source().sendFailure(Messages.RUN_REFUSED.get(current.ownerName(), current.description(), current.percent(), current.done(), current.total()));
            return false;
        }
        active = run;
        tickCounter = 0;
        run.start();
        long seconds = (long) run.total() * MAConfig.jobIntervalTicks() / 20L;
        run.message(Messages.RUN_STARTED.get(run.id(), run.description(), run.total(), Run.formatSeconds(seconds)));
        return true;
    }

    public static boolean cancel(CommandSourceStack requester) {
        Run current = active;
        if (current == null) {
            requester.sendFailure(Messages.RUN_NOTHING.get());
            return false;
        }
        active = null;
        current.cancel();
        var text = Messages.RUN_CANCELLED.get(current.id(), current.description(), current.ownerName(), current.done(), current.total());
        requester.sendSuccess(() -> text, true);
        if (!requester.getTextName().equals(current.ownerName())) {
            current.message(text);
        }
        return true;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        Run current = active;
        if (current == null) {
            return;
        }
        tickCounter++;
        if (tickCounter % MAConfig.jobIntervalTicks() != 0) {
            return;
        }
        Runnable job = current.nextJob();
        if (job == null) {
            finish(current);
            return;
        }
        try {
            job.run();
            current.markDone();
        } catch (Throwable error) {
            active = null;
            current.fail(error);
            return;
        }
        if (current.remaining() == 0) {
            finish(current);
        }
    }

    private static void finish(Run run) {
        active = null;
        try {
            run.complete();
        } catch (Throwable error) {
            run.fail(error);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Run current = active;
        if (current == null) {
            return;
        }
        active = null;
        current.cancel();
        ModpackAssistant.LOGGER.info("Run #{} ({}) cancelled because the server is stopping", current.id(), current.description());
    }
}
