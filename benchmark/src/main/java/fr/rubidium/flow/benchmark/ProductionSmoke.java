package fr.rubidium.flow.benchmark;

import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import java.nio.file.*;
import java.util.*;

/** Loaded under a distinct dev mod ID, so the player's production guards are exercised. */
public final class ProductionSmoke implements ModInitializer {
    private int ticks;
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(++ticks!=3) return;
            try {
                long samplers=Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals("Rubidium-Pulse")).count();
                boolean redirected=Arrays.stream(NoiseBasedChunkGenerator.class.getDeclaredMethods())
                        .anyMatch(m -> m.getName().contains("rubidium$paceNoise"));
                if(samplers!=0 || redirected) throw new AssertionError("Research instrumentation active in player mode");
                long math=MathAudit.verify();
                Map<String,Object> report=Map.of("mode","production-smoke","performanceValid",false,
                        "productionGuardsPass",true,"pulseThreads",samplers,"noiseRedirectPresent",redirected,"exactMathCases",math);
                Files.writeString(Path.of("benchmark-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
                System.out.println("RUBIDIUM_PRODUCTION_SMOKE_OK math="+math);
                server.halt(false);
            } catch(Exception error) { throw new RuntimeException(error); }
        });
    }
}
