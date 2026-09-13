package fr.rubidium.flow.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AsyncGateTest {
    static class ManualExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void next() { tasks.remove().run(); }
    }
    @Test void limitsConcurrentSuppliersWithoutBlockingCaller() {
        var executor = new ManualExecutor(); var gate = new AsyncGate(1, 4);
        var a = gate.submit(() -> 11, executor); var b = gate.submit(() -> 22, executor);
        assertEquals(1, executor.tasks.size()); assertEquals(1, gate.queued());
        assertFalse(a.isDone()); assertFalse(b.isDone());
        executor.next(); assertEquals(11, a.join()); assertFalse(b.isDone());
        executor.next(); assertEquals(22, b.join()); assertEquals(0, gate.running());
    }
    @Test void failedSupplierReleasesPermitAndPreservesFailure() {
        var executor = new ManualExecutor(); var gate = new AsyncGate(1, 2);
        var error = new IllegalStateException("expected");
        var a = gate.submit(() -> { throw error; }, executor);
        var b = gate.submit(() -> 22, executor);
        executor.next(); assertSame(error, assertThrows(CompletionException.class, a::join).getCause());
        executor.next(); assertEquals(22, b.join()); assertEquals(0, gate.running());
    }
    @Test void rejectingExecutorDoesNotLeakPermit() {
        var gate = new AsyncGate(1, 2);
        var a = gate.submit(() -> 11, r -> { throw new RejectedExecutionException(); });
        assertThrows(CompletionException.class, a::join);
        assertEquals(22, gate.submit(() -> 22, Runnable::run).join());
        assertEquals(0, gate.running());
    }
    @Test void saturationUsesOriginalExecutorAndKeepsQueueBounded() {
        var executor = new ManualExecutor(); var gate = new AsyncGate(1, 1);
        var a = gate.submit(() -> 11, executor); var b = gate.submit(() -> 22, executor);
        var c = gate.submit(() -> 33, executor);
        assertEquals(1, gate.queued()); assertEquals(1, gate.bypassed());
        assertEquals(2, executor.tasks.size());
        while (!executor.tasks.isEmpty()) executor.next();
        assertEquals(List.of(11,22,33), List.of(a.join(),b.join(),c.join()));
    }
    @Test void increasingLimitStartsWaitingTasksAndLoweringDoesNotCancelRunning() {
        var executor = new ManualExecutor(); var gate = new AsyncGate(1, 8);
        var all = new ArrayList<CompletableFuture<Integer>>();
        for(int i=0;i<4;i++) all.add(gate.submit(() -> 7, executor));
        gate.setLimit(3); assertEquals(3, executor.tasks.size());
        gate.setLimit(1); executor.next(); assertEquals(2, gate.running());
        while(!executor.tasks.isEmpty()) executor.next();
        assertTrue(all.stream().allMatch(CompletableFuture::isDone));
    }
    @Test void closeDrainsAndLaterSubmissionsFallBackWithoutDroppingTasks() {
        var executor = new ManualExecutor(); var gate = new AsyncGate(1, 4);
        var a=gate.submit(() -> 11, executor); var b=gate.submit(() -> 22, executor);
        gate.close(); var c=gate.submit(() -> 33, executor);
        assertEquals(0, gate.queued());
        while(!executor.tasks.isEmpty()) executor.next();
        assertEquals(66, a.join()+b.join()+c.join());
    }
    @Test void directExecutorAndLongQueueDoNotOverflowCallStack() {
        var hold = new ManualExecutor(); var gate = new AsyncGate(1, 10000);
        gate.submit(() -> 0, hold);
        var completed = new AtomicInteger();
        for(int i=0;i<9000;i++) gate.submit(completed::incrementAndGet, Runnable::run);
        hold.next(); assertEquals(9000, completed.get()); assertEquals(0, gate.running());
    }
    @Test void concurrentSubmissionCompletesEverySupplierExactlyOnce() throws Exception {
        var gate = new AsyncGate(3, 64); var calls = new AtomicInteger();
        try(var executor=Executors.newFixedThreadPool(6)) {
            var all = new ArrayList<CompletableFuture<Integer>>();
            for(int i=0;i<2000;i++) all.add(gate.submit(calls::incrementAndGet,executor));
            CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).get(15,TimeUnit.SECONDS);
            assertEquals(2000,calls.get()); assertEquals(0,gate.running());
            assertEquals(2000,all.stream().map(CompletableFuture::join).distinct().count());
        }
    }

    @Test void dispatchesSuccessorBeforeRunningInlineCompletionCallbacks() throws Exception {
        var gate = new AsyncGate(1, 4);
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = gate.submit(() -> {
                firstStarted.countDown();
                try {
                    assertTrue(releaseFirst.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    throw new CompletionException(interrupted);
                }
                return 11;
            }, executor);
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            var second = gate.submit(() -> 22, executor);
            var callback = first.thenApply(value -> {
                try {
                    return value + second.get(1, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            });

            releaseFirst.countDown();

            assertEquals(33, callback.get(2, TimeUnit.SECONDS));
            assertEquals(0, gate.running());
        } finally {
            releaseFirst.countDown();
            gate.close();
        }
    }

    @Test void cancelledQueuedJobSkipsSupplierAndReleasesPermitForFollowingWork() {
        var executor = new ManualExecutor();
        var gate = new AsyncGate(1, 4);
        var cancelledCalls = new AtomicInteger();
        var first = gate.submit(() -> 11, executor);
        var cancelled = gate.submit(cancelledCalls::incrementAndGet, executor);
        var third = gate.submit(() -> 33, executor);

        assertTrue(cancelled.cancel(false));
        executor.next();
        assertEquals(11, first.join());
        executor.next();
        assertTrue(cancelled.isCancelled());
        assertEquals(0, cancelledCalls.get());
        executor.next();

        assertEquals(33, third.join());
        assertEquals(0, gate.running());
        assertEquals(0, gate.queued());
    }
}
