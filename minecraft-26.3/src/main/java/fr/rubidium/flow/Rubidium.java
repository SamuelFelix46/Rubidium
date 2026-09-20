package fr.rubidium.flow;

import net.fabricmc.api.ModInitializer;
import org.slf4j.LoggerFactory;

public final class Rubidium implements ModInitializer {
    @Override
    public void onInitialize() {
        LoggerFactory.getLogger("Rubidium").info(
                "Rubidium 26.3: the exact structure fast path is already native; no mixin or rendering change applied.");
    }
}
