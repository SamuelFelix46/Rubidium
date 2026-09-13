package fr.rubidium.flow.clientbench;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.GlUtil;
import fr.rubidium.flow.RuntimeMonitor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.minecraft.client.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import java.nio.file.*;
import java.util.*;

/** Visible test game using only the copied world named bench-world in its isolated game directory. */
public final class ClientBenchmark implements ClientModInitializer {
    private static ClientBenchmark instance;
    private final String mode=System.getProperty("rubidium.bench.mode","observe");
    private final double[] frames=new double[200_000];
    private final List<Map<String,Object>> samples=new ArrayList<>();
    private long loadedAt,start,lastFrame,lastSample,noiseStart;
    private int frameCount,unfocusedFrames,screenFrames,lastStop=-1;
    private boolean done,opened;
    public void onInitializeClient() {
        if(!Set.of("observe","active").contains(mode)) throw new IllegalArgumentException("Unknown mode");
        instance=this;
        RuntimeMonitor.setObservationOnly(mode.equals("observe") || Boolean.getBoolean("rubidium.bench.noGate"));
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if(!Files.isDirectory(client.gameDirectory.toPath().resolve("saves/bench-world")))
                throw new IllegalStateException("Isolated bench-world must exist");
            client.options.pauseOnLostFocus=false;
            client.options.onboardAccessibility=false;
            client.options.enableVsync().set(false);
            client.options.framerateLimit().set(260);
            client.options.renderDistance().set(8);
            client.options.simulationDistance().set(5);
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(Minecraft client) {
        // Wait for the initial resource overlay to finish before opening a world.
        // doWorldLoad renders with runTick(false), which cannot finish that initial overlay.
        if(!opened && client.getOverlay()==null && client.level==null) {
            opened=true;
            System.out.println("RUBIDIUM_CLIENT_OPEN from="+(client.screen==null?"none":client.screen.getClass().getName()));
            client.createWorldOpenFlows().openWorld("bench-world",client::stop);
        }
        if(done || client.level==null || client.player==null || client.screen!=null) return;
        long now=System.nanoTime();
        if(loadedAt==0) {
            loadedAt=now;
            var server=client.getSingleplayerServer();
            if(server==null) throw new IllegalStateException("Integrated server missing during warmup");
            server.execute(() -> {
                var player=server.getPlayerList().getPlayer(client.getUser().getProfileId());
                if(player==null) throw new IllegalStateException("Test player missing during warmup");
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(server.overworld(),60.5,110,-40.5,-90,25);
            });
            return;
        }
        if(now-loadedAt<20_000_000_000L) return;
        if(start==0) {
            start=now; noiseStart=RuntimeMonitor.stats().noiseTasks();
            System.out.println("RUBIDIUM_CLIENT_START mode="+mode);
        }
        if(now-start>=60_000_000_000L) { finish(client); return; }
        int stop=(int)((now-start)/15_000_000_000L);
        if(stop!=lastStop) {
            lastStop=stop;
            var server=client.getSingleplayerServer();
            if(server==null) throw new IllegalStateException("Integrated server missing");
            final int index=stop;
            server.execute(() -> {
                var player=server.getPlayerList().getPlayer(client.getUser().getProfileId());
                if(player==null) throw new IllegalStateException("Test player missing");
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(server.overworld(),32_000+index*256,120,32_000,-90,25);
            });
        }
        if(now-lastSample>=1_000_000_000L) {
            lastSample=now;
            var gate=RuntimeMonitor.stats();
            Map<String,Object> point=new LinkedHashMap<>();
            point.put("elapsedSeconds",(now-start)/1e9); point.put("stop",stop);
            point.put("renderedSections",client.levelRenderer.countRenderedSections());
            point.put("sections",client.levelRenderer.getSectionStatistics());
            point.put("noiseTasks",gate.noiseTasks()-noiseStart); point.put("gate",gate);
            samples.add(point);
        }
    }
    public static void frame(Minecraft client) {
        ClientBenchmark b=instance;
        if(b==null || b.start==0 || b.done) return;
        long now=System.nanoTime();
        if(b.lastFrame!=0) {
            if(b.frameCount==b.frames.length) throw new IllegalStateException("Frame buffer full");
            b.frames[b.frameCount++]=(now-b.lastFrame)/1e6;
            if(!client.isWindowActive()) b.unfocusedFrames++;
            if(client.screen!=null) b.screenFrames++;
        }
        b.lastFrame=now;
    }
    private void finish(Minecraft client) {
        done=true;
        try {
            Map<String,Object> report=new LinkedHashMap<>();
            boolean uploads=Arrays.stream(net.minecraft.client.renderer.LevelRenderer.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("rubidium$budgetUploads"));
            if(uploads != (mode.equals("active") && Boolean.getBoolean("rubidium.bench.experimentalUploads"))) throw new IllegalStateException("Unexpected upload hook state");
            report.put("uploadHookPresent",uploads);
            report.put("pulseThreads",Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals("Rubidium-Pulse")).count());
            report.put("schema",1); report.put("minecraft","1.21.1"); report.put("mode",mode);
            report.put("scenarioRevision",2); report.put("javaRuntime",System.getProperty("java.runtime.version"));
            report.put("gateEnabled",mode.equals("active") && !Boolean.getBoolean("rubidium.bench.noGate"));
            report.put("scenario","20s spawn warmup, four new terrain stops 15s apart, 60s frame capture");
            report.put("graphics",client.options.graphicsMode().get().toString());
            report.put("gpu",GlUtil.getRenderer()); report.put("cpu",GlUtil.getCpuInfo());
            report.put("width",client.getWindow().getWidth()); report.put("height",client.getWindow().getHeight());
            report.put("renderDistance",client.options.renderDistance().get()); report.put("vsync",false);
            report.put("fpsLimit",client.options.framerateLimit().get());
            report.put("unfocusedFrames",unfocusedFrames); report.put("screenFrames",screenFrames);
            report.put("frameMs",Arrays.copyOf(frames,frameCount)); report.put("samples",samples);
            Files.writeString(client.gameDirectory.toPath().resolve("client-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report));
            try(var shot=Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                shot.writeToFile(client.gameDirectory.toPath().resolve("client-final.png"));
            }
            System.out.println("RUBIDIUM_CLIENT_OK frames="+frameCount);
            client.stop();
        } catch(Exception error) { throw new RuntimeException("Client benchmark failed",error); }
    }
}


