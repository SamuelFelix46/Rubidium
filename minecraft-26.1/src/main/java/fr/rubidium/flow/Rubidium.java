package fr.rubidium.flow;
import net.fabricmc.api.ModInitializer;
import org.slf4j.LoggerFactory;
public final class Rubidium implements ModInitializer {
    public void onInitialize() {
        LoggerFactory.getLogger("Rubidium").info("Rubidium 26.1: exact structure calculation fast path; this port does not modify the new renderer.");
    }
}
