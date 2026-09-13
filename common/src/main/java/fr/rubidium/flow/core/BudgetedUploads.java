package fr.rubidium.flow.core;

import java.util.Objects;
import java.util.Queue;
import java.util.function.LongSupplier;

public final class BudgetedUploads {
    private BudgetedUploads() {
    }

    public static void drain(
            Queue<Runnable> queue,
            LongSupplier clock,
            long budgetNanos,
            int maxJobs) {
        Objects.requireNonNull(queue, "queue");
        Objects.requireNonNull(clock, "clock");

        long startedAt = clock.getAsLong();
        Runnable job = queue.poll();
        if (job == null) {
            return;
        }

        int completed = 0;
        int jobLimit = Math.max(1, maxJobs);
        do {
            job.run();
            completed++;
            if (completed >= jobLimit || clock.getAsLong() - startedAt >= budgetNanos) {
                return;
            }
            job = queue.poll();
        } while (job != null);
    }
}
