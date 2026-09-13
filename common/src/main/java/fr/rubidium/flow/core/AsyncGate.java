package fr.rubidium.flow.core;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Nonblocking admission for independent synchronous suppliers on their original executor.
 * Suppliers must be independent and completion continuations must remain nonblocking.
 * In particular, work using a direct or single-thread executor cannot synchronously await
 * another job on that same executor. Overflow and close deliberately fall back to the
 * original executor: the capacity bounds this queue, not the entire JVM.
 */
public final class AsyncGate implements AutoCloseable {
    private final ArrayDeque<Job<?>> queue = new ArrayDeque<>();
    private final AtomicInteger draining = new AtomicInteger();
    private final int capacity;
    private int limit, running;
    private long bypassed;
    private boolean closed;
    public AsyncGate(int limit, int capacity) {
        if(limit<1 || capacity<1) throw new IllegalArgumentException("Positive limit and capacity required");
        this.limit=limit; this.capacity=capacity;
    }
    public <T> CompletableFuture<T> submit(Supplier<T> supplier, Executor executor) {
        Objects.requireNonNull(supplier); Objects.requireNonNull(executor);
        Job<T> job=new Job<>(supplier,executor);
        boolean bypass;
        synchronized(this) {
            bypass=closed || queue.size()>=capacity;
            if(bypass) bypassed++; else queue.addLast(job);
        }
        if(bypass) job.dispatch(false); else drain();
        return job.result;
    }
    public void setLimit(int limit) {
        if(limit<1) throw new IllegalArgumentException("Positive limit required");
        synchronized(this) { this.limit=limit; }
        drain();
    }
    public synchronized int queued() { return queue.size(); }
    public synchronized int running() { return running; }
    public synchronized long bypassed() { return bypassed; }
    public void close() {
        synchronized(this) { closed=true; }
        drain();
    }
    private void drain() {
        // Trampoline also supports direct executors without a recursive completion chain.
        if(draining.getAndIncrement()!=0) return;
        int missed=1;
        do {
            while(true) {
                Job<?> job;
                synchronized(this) {
                    if(queue.isEmpty() || (!closed && running>=limit)) break;
                    job=queue.removeFirst(); running++;
                }
                job.dispatch(true);
            }
            missed=draining.addAndGet(-missed);
        } while(missed!=0);
    }
    private final class Job<T> {
        private final Supplier<T> supplier;
        private final Executor executor;
        private final CompletableFuture<T> result=new CompletableFuture<>();
        private Job(Supplier<T> supplier,Executor executor) { this.supplier=supplier; this.executor=executor; }
        private void dispatch(boolean admitted) {
            try { executor.execute(() -> run(admitted)); }
            catch(RuntimeException rejection) { finish(null,rejection,admitted); }
        }
        private void run(boolean admitted) {
            if(result.isDone()) {
                finish(null,null,admitted);
                return;
            }
            T value=null; Throwable failure=null;
            try { value=supplier.get(); }
            catch(Throwable thrown) { failure=thrown; }
            finish(value,failure,admitted);
        }
        private void finish(T value,Throwable failure,boolean admitted) {
            if(admitted) synchronized(AsyncGate.this) { running--; }
            if(admitted) drain();
            if(result.isDone()) return;
            if(failure==null) result.complete(value); else result.completeExceptionally(failure);
        }
    }
}
