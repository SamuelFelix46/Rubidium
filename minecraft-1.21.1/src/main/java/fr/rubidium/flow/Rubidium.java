package fr.rubidium.flow;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Rubidium implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Rubidium Flow");
    @Override public void onInitialize() {
        LOG.info("Rubidium 1.21.1: exact structure fast path; native rendering unchanged; no configuration.");
        for (String mod : new String[]{"sodium","lithium","ferritecore","immediatelyfast","krypton","modernfix","c2me"}) {
            if (FabricLoader.getInstance().isModLoaded(mod)) LOG.info("Detected {} (detection is not compatibility certification)", mod);
        }
        // The rejected admission prototype is retained only for reproducible research.
        // Player installations create no sampler, telemetry window or generation gate.
        if(!FabricLoader.getInstance().isModLoaded("rubidium_benchmark")
                && !FabricLoader.getInstance().isModLoaded("rubidium_client_benchmark")) return;
        ServerLifecycleEvents.SERVER_STARTING.register(server -> RuntimeMonitor.start());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RuntimeMonitor.stop());
        ServerTickEvents.START_SERVER_TICK.register(server -> RuntimeMonitor.tickStart());
        ServerTickEvents.END_SERVER_TICK.register(server -> RuntimeMonitor.tickEnd());
    }
}

