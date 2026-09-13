package fr.rubidium.flow.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class BudgetedUploadsTest {
    @Test
    void executesInFifoOrderUpToJobLimit() {
        List<Integer> executed = new ArrayList<>();
        Queue<Runnable> queue = numberedQueue(executed, 1, 2, 3);

        BudgetedUploads.drain(queue, () -> 0L, Long.MAX_VALUE, 2);

        assertEquals(List.of(1, 2), executed);
        assertEquals(1, queue.size());
    }

    @Test
    void stopsBeforePollingNextJobWhenBudgetIsReached() {
        List<Integer> executed = new ArrayList<>();
        Queue<Runnable> queue = numberedQueue(executed, 1, 2, 3);

        BudgetedUploads.drain(queue, new SequenceClock(0L, 5L, 10L), 10L, 64);

        assertEquals(List.of(1, 2), executed);
        assertEquals(1, queue.size());
    }

    @Test
    void executesOneExpensiveJobEvenWhenItExceedsBudget() {
        List<Integer> executed = new ArrayList<>();
        Queue<Runnable> queue = numberedQueue(executed, 1, 2);

        BudgetedUploads.drain(queue, new SequenceClock(0L, 100L), 10L, 64);

        assertEquals(List.of(1), executed);
        assertEquals(1, queue.size());
    }

    @Test
    void repeatedFramesEventuallyDrainEveryJobWithoutReordering() {
        List<Integer> executed = new ArrayList<>();
        Queue<Runnable> queue = numberedQueue(executed, 1, 2, 3, 4, 5);

        while (!queue.isEmpty()) {
            BudgetedUploads.drain(queue, () -> 0L, Long.MAX_VALUE, 2);
        }

        assertEquals(List.of(1, 2, 3, 4, 5), executed);
    }

    @Test
    void jobsEnqueuedByJobsRemainBoundedBySameFrameLimit() {
        Queue<Runnable> queue = new ArrayDeque<>();
        AtomicInteger executions = new AtomicInteger();
        Runnable[] repeating = new Runnable[1];
        repeating[0] = () -> {
            executions.incrementAndGet();
            queue.add(repeating[0]);
        };
        queue.add(repeating[0]);

        BudgetedUploads.drain(queue, () -> 0L, Long.MAX_VALUE, 3);

        assertEquals(3, executions.get());
        assertEquals(1, queue.size());
    }

    @Test
    void nonpositiveLimitsStillAllowOneQueuedJob() {
        List<Integer> executed = new ArrayList<>();
        Queue<Runnable> queue = numberedQueue(executed, 1, 2);

        BudgetedUploads.drain(queue, () -> 0L, 0L, 0);

        assertEquals(List.of(1), executed);
        assertEquals(1, queue.size());
    }

    @Test
    void propagatesJobExceptionAndLeavesFollowingJobQueued() {
        Queue<Runnable> queue = new ArrayDeque<>();
        IllegalStateException expected = new IllegalStateException("expected");
        queue.add(() -> { throw expected; });
        queue.add(() -> { });

        IllegalStateException actual = assertThrows(
                IllegalStateException.class,
                () -> BudgetedUploads.drain(queue, () -> 0L, Long.MAX_VALUE, 64));

        assertEquals(expected, actual);
        assertEquals(1, queue.size());
    }

    private static Queue<Runnable> numberedQueue(List<Integer> executed, int... values) {
        Queue<Runnable> queue = new ArrayDeque<>();
        for (int value : values) {
            queue.add(() -> executed.add(value));
        }
        return queue;
    }

    private static final class SequenceClock implements LongSupplier {
        private final long[] values;
        private int index;

        private SequenceClock(long... values) {
            this.values = values;
        }

        @Override
        public long getAsLong() {
            return values[Math.min(index++, values.length - 1)];
        }
    }
}
