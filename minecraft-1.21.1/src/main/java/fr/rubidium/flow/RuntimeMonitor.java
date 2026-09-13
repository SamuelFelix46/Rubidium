package fr.rubidium.flow;

import fr.rubidium.flow.core.AsyncGate;
import fr.rubidium.flow.core.FrameWindow;
import fr.rubidium.flow.core.IntervalTelemetry;
import fr.rubidium.flow.core.PulseController;
import java.lang.management.ManagementFactory;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/** One monitor per integrated/dedicated server, independent of client classes. */
public final class RuntimeMonitor {
    private static volatile RuntimeMonitor current;
    private static volatile boolean observationOnly;
    private static final FrameWindow FRAMES = new FrameWindow(512);
    private static final AtomicLong FRAME_PEAK = new AtomicLong();
    private static volatile long lastFrameAt;
    private final PulseController controller = new PulseController(Runtime.getRuntime().availableProcessors());
    private final AsyncGate gate = new AsyncGate(controller.limit(), 64);
    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Rubidium-Pulse"); thread.setDaemon(true); return thread;
    });
    private final LongAdder noiseTasks = new LongAdder();
    private final LongAdder noiseNanos = new LongAdder();
    private final IntervalTelemetry ticks = new IntervalTelemetry();
    private final com.sun.management.OperatingSystemMXBean operatingSystem;
    private volatile double tickMs;
    private long tickStart;
    private double frameBaseline;
    private volatile boolean failed;

    private RuntimeMonitor() {
        var bean = ManagementFactory.getOperatingSystemMXBean();
        operatingSystem = bean instanceof com.sun.management.OperatingSystemMXBean extended
                ? extended : null;
    }
    public static synchronized void start() {
        stop();
        RuntimeMonitor monitor = new RuntimeMonitor(); current = monitor;
        monitor.sampler.scheduleAtFixedRate(monitor::sampleSafely, 250, 250, TimeUnit.MILLISECONDS);
        Rubidium.LOG.info("Pulse ready: {} logical processors, heap max {} MiB, admission {}/{}, queue capacity 64",
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / 1048576,
                monitor.controller.limit(), monitor.controller.ceiling());
    }
    public static synchronized void stop() {
        RuntimeMonitor monitor = current; current = null;
        if (monitor != null) { monitor.sampler.shutdown(); monitor.gate.close(); }
        clearFrames();
    }
    /** Test harness switch; no user configuration is read or written. */
    public static void setObservationOnly(boolean observe) { observationOnly = observe; }
    public static void tickStart() { RuntimeMonitor m=current; if(m!=null) m.tickStart=System.nanoTime(); }
    public static void tickEnd() {
        RuntimeMonitor monitor=current;
        if(monitor!=null) {
            long now=System.nanoTime();
            long duration=now-monitor.tickStart;
            monitor.tickMs=duration/1e6;
            monitor.ticks.record(duration,now);
        }
    }
    public static void recordFrame(long nanos) {
        RuntimeMonitor monitor=current;
        if(monitor==null || nanos<=0) return;
        FRAMES.record(nanos);
        FRAME_PEAK.accumulateAndGet(nanos,Math::max);
        lastFrameAt=System.nanoTime();
    }
    public static void clearFrames() {
        FRAMES.clear();
        FRAME_PEAK.set(0);
        lastFrameAt=0;
        RuntimeMonitor monitor=current;
        if(monitor!=null) monitor.frameBaseline=0;
    }
    public static FrameWindow.Snapshot frames() { return FRAMES.snapshot(); }

    public static <T> CompletableFuture<T> noise(Supplier<T> vanilla, Executor executor) {
        RuntimeMonitor monitor=current;
        if(monitor==null) return CompletableFuture.supplyAsync(vanilla,executor);
        Supplier<T> measured = () -> {
            long start=System.nanoTime();
            try { return vanilla.get(); }
            finally { monitor.noiseNanos.add(System.nanoTime()-start); monitor.noiseTasks.increment(); }
        };
        return observationOnly || monitor.failed
                ? CompletableFuture.supplyAsync(measured,executor) : monitor.gate.submit(measured,executor);
    }
    private void sampleSafely() {
        try { sample(); }
        catch(RuntimeException failure) {
            failed=true; gate.close(); sampler.shutdown();
            Rubidium.LOG.error("Pulse control disabled; existing work drains on the original executor",failure);
        }
    }
    private void sample() {
        long now=System.nanoTime();
        double cpu=-1;
        if(operatingSystem!=null) {
            double reading=operatingSystem.getProcessCpuLoad();
            if(Double.isFinite(reading) && reading>=0 && reading<=1) cpu=reading;
        }
        Runtime runtime=Runtime.getRuntime();
        double heap=(double)(runtime.totalMemory()-runtime.freeMemory())/runtime.maxMemory();
        var tickSample=ticks.drain();
        double tick=-1;
        if(tickSample.isFreshAt(now,TimeUnit.SECONDS.toNanos(2))) {
            tick=tickSample.maxMs()>=150 ? tickSample.maxMs() : tickSample.meanMs();
        }
        long recentPeak=FRAME_PEAK.getAndSet(0);
        long frameAt=lastFrameAt;
        double frame=-1;
        double baseline=-1;
        if(frameAt!=0 && now-frameAt<=TimeUnit.SECONDS.toNanos(2)) {
            var window=FRAMES.snapshot();
            if(window.count()>=30) {
                frame=Math.max(recentPeak/1e6,window.p95Ms());
                if(frameBaseline<=0) frameBaseline=window.medianMs();
                else if(window.medianMs()<frameBaseline*1.25) frameBaseline=frameBaseline*.95+window.medianMs()*.05;
                baseline=frameBaseline;
            }
        } else frameBaseline=0;
        gate.setLimit(controller.update(now,cpu,heap,tick,frame,baseline,gate.queued()));
    }
    public static Stats stats() {
        RuntimeMonitor m=current;
        return m==null ? new Stats(0,0,0,0,0,0,0) : new Stats(m.noiseTasks.sum(),m.noiseNanos.sum(),
                m.gate.bypassed(),m.gate.running(),m.gate.queued(),m.controller.limit(),m.tickMs);
    }
    public record Stats(long noiseTasks,long noiseNanos,long bypassed,int running,int queued,int limit,double tickMs) { }
}
