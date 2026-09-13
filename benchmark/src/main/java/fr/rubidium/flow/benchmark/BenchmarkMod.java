package fr.rubidium.flow.benchmark;

import com.google.gson.GsonBuilder;
import fr.rubidium.flow.RuntimeMonitor;
import fr.rubidium.flow.benchmark.mixin.ServerChunkCacheInvoker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;

/** Dedicated isolated-world benchmark. Never shipped inside the player JAR. */
public final class BenchmarkMod implements ModInitializer {
    private static final TicketType<ChunkPos> TICKET=TicketType.create("rubidium_benchmark",Comparator.comparingLong(ChunkPos::toLong));
    private final String mode=System.getProperty("rubidium.bench.mode","observe");
    private final String dimension=System.getProperty("rubidium.bench.dimension","overworld");
    private final int side=Integer.parseInt(System.getProperty("rubidium.bench.side","8"));
    private final List<ChunkAccess> chunks=new ArrayList<>();
    private final List<ChunkPos> heldTickets=new ArrayList<>();
    private final List<Double> tickTimes=new ArrayList<>();
    private final List<Double> tickCpuTimes=new ArrayList<>();
    private final ThreadMXBean threads=ManagementFactory.getThreadMXBean();
    private final List<Double> latencyMs=new ArrayList<>();
    private MinecraftServer server;
    private ServerLevel world;
    private boolean measuring,done;
    private int index,inFlight,finished;
    private long start,deadline,cpuStart,gcStart,noiseStart,peakHeap,tickBegin;
    private int maxQueue,maxRunning,minLimit=Integer.MAX_VALUE,maxLimit;
    private long bypassStart;
    private long quietUntil;
    private int initialLimit;
    private long mathCases;
    private long tickCpuBegin;
    @Override public void onInitialize() {
        if(side<1 || side>64) throw new IllegalArgumentException("Benchmark side must be 1..64");
        if(!mode.equals("active") && !mode.equals("observe")) throw new IllegalArgumentException("Unknown benchmark mode");
        RuntimeMonitor.setObservationOnly(mode.equals("observe") || Boolean.getBoolean("rubidium.bench.noGate"));
        if(NoiseAudit.ENABLED) mathCases=MathAudit.verify();
        ServerLifecycleEvents.SERVER_STARTED.register(this::begin);
        ServerTickEvents.START_SERVER_TICK.register(s -> {
            tickBegin=System.nanoTime();
            tickCpuBegin=threads.isCurrentThreadCpuTimeSupported()?threads.getCurrentThreadCpuTime():-1;
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> tick());
    }
    private void begin(MinecraftServer server) {
        this.server=server;
        world=server.getLevel(switch(dimension) { case "nether" -> Level.NETHER; case "end" -> Level.END; default -> Level.OVERWORLD; });
        world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
        world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        // Warm the generator on a distinct 4x4 region before timing new coordinates.
        deadline=System.nanoTime()+600_000_000_000L;
        System.out.println("RUBIDIUM_BENCH_WARMUP mode="+mode+" dimension="+dimension);
    }
    private void tick() {
        if(server==null || done) return;
        if(System.nanoTime()>deadline) { fail(new IllegalStateException("Benchmark exceeded ten minutes")); return; }
        if(measuring) {
            tickTimes.add((System.nanoTime()-tickBegin)/1e6);
            tickCpuTimes.add(tickCpuBegin<0?-1:(threads.getCurrentThreadCpuTime()-tickCpuBegin)/1e6);
            peakHeap=Math.max(peakHeap,Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
            var stats=RuntimeMonitor.stats(); maxQueue=Math.max(maxQueue,stats.queued());
            maxRunning=Math.max(maxRunning,stats.running()); minLimit=Math.min(minLimit,stats.limit()); maxLimit=Math.max(maxLimit,stats.limit());
        }
        int count=measuring?side*side:16;
        if(finished==count && inFlight==0) {
            if(!measuring) {
                // Retain warmup tickets: avoid measuring their asynchronous unload/save work.
                if(quietUntil==0) quietUntil=System.nanoTime()+5_000_000_000L;
                if(System.nanoTime()<quietUntil) return;
                chunks.clear(); index=0; finished=0; measuring=true;
                start=System.nanoTime(); cpuStart=cpuTime(); gcStart=gcTime();
                noiseStart=RuntimeMonitor.stats().noiseTasks(); bypassStart=RuntimeMonitor.stats().bypassed();
                initialLimit=RuntimeMonitor.stats().limit();
                System.out.println("RUBIDIUM_BENCH_START chunks="+(side*side));
            } else { finish(); return; }
        }
        count=measuring?side*side:16;
        while(inFlight<8 && index<count) {
            int width=measuring?side:4; int origin=measuring?1000:500;
            ChunkPos pos=new ChunkPos(origin+index%width,origin+index/width);
            index++; inFlight++;
            if(!server.isSameThread()) throw new IllegalStateException("Off-thread benchmark submission");
            world.getChunkSource().addRegionTicket(TICKET,pos,0,pos);
            heldTickets.add(pos);
            long submitted=System.nanoTime(); boolean timed=measuring;
            ((ServerChunkCacheInvoker)world.getChunkSource()).rubidium$requestAsync(pos.x,pos.z,ChunkStatus.FULL,true)
                .whenComplete((result,error) -> server.execute(() -> {
                    if(done) return;
                    if(!server.isSameThread()) throw new IllegalStateException("Off-thread benchmark completion");
                    if(error!=null) { fail(error); return; }
                    ChunkAccess chunk=result.orElse(null);
                    if(chunk==null) { fail(new IllegalStateException(result.getError())); return; }
                    chunks.add(chunk); finished++; inFlight--;
                    if(timed) latencyMs.add((System.nanoTime()-submitted)/1e6);
                }));
        }
    }
    private void finish() {
        long end=System.nanoTime(), cpu=cpuTime()-cpuStart, gc=gcTime()-gcStart;
        done=true;
        try {
            var stats=RuntimeMonitor.stats();
            Map<String,Object> report=new LinkedHashMap<>();
            String runType=NoiseAudit.ENABLED?"integrity":Boolean.getBoolean("rubidium.bench.profile")?"profile":"performance";
            report.put("runType",runType); report.put("performanceValid",runType.equals("performance"));
            report.put("schema",1); report.put("minecraft","1.21.1"); report.put("mod","0.1.0-alpha.1");
            report.put("mode",mode); report.put("baseline","Fabric + Fabric API + identical instrumentation");
            report.put("gateEnabled",mode.equals("active") && !Boolean.getBoolean("rubidium.bench.noGate"));
            report.put("dimension",dimension); report.put("seed",world.getSeed()); report.put("chunks",chunks.size());
            report.put("origin",List.of(1000,1000)); report.put("side",side); report.put("requestConcurrency",8);
            report.put("java",System.getProperty("java.runtime.version")); report.put("os",System.getProperty("os.name"));
            report.put("processors",Runtime.getRuntime().availableProcessors()); report.put("maxHeapBytes",Runtime.getRuntime().maxMemory());
            report.put("seconds",(end-start)/1e9); report.put("chunksPerSecond",chunks.size()*1e9/(end-start));
            report.put("processCpuSeconds",cpu/1e9); report.put("gcPauseCollectionMs",gc); report.put("peakSampledHeapBytes",peakHeap);
            report.put("noiseTasksIncludingNeighbors",stats.noiseTasks()-noiseStart);
            report.put("initialLimit",initialLimit);
            report.put("gateOverflowBypasses",stats.bypassed()-bypassStart); report.put("maxGateQueue",maxQueue);
            report.put("maxAdmitted",maxRunning); report.put("minLimit",minLimit); report.put("maxLimit",maxLimit);
            report.put("tickMs",tickTimes); report.put("requestLatencyMs",latencyMs);
            report.put("tickCpuMs",tickCpuTimes);
            report.put("clientFps",null);
            report.put("noiseAuditEnabled",NoiseAudit.ENABLED);
            report.put("exactMathCases",mathCases);
            if(NoiseAudit.ENABLED) NoiseAudit.requireComplete();
            report.put("noiseStageFingerprints",NoiseAudit.snapshot());
            report.put("finalWorldIntegrity","not assessed: FULL completion does not prove world-pipeline quiescence");
            Files.writeString(Path.of("benchmark-result.json"),new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report));
            System.out.println("RUBIDIUM_BENCH_OK seconds="+report.get("seconds")+" chunks="+chunks.size());
            releaseTickets();
            server.halt(false);
        } catch(Exception failure) { fail(failure); }
    }
    private void fail(Throwable error) {
        done=true; error.printStackTrace();
        releaseTickets();
        try { Files.writeString(Path.of("benchmark-failure.txt"),error.toString()); } catch(Exception ignored) { }
        if(server!=null) server.halt(false);
    }
    private void releaseTickets() {
        if(world!=null) for(ChunkPos pos:heldTickets) world.getChunkSource().removeRegionTicket(TICKET,pos,0,pos);
        heldTickets.clear();
    }
    private static long cpuTime() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os ? os.getProcessCpuTime():0;
    }
    private static long gcTime() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0,b.getCollectionTime())).sum(); }
}
