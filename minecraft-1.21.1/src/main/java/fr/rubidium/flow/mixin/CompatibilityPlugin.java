package fr.rubidium.flow.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;

public final class CompatibilityPlugin implements IMixinConfigPlugin {
    public boolean shouldApplyMixin(String target, String mixin) {
        boolean harness=FabricLoader.getInstance().isModLoaded("rubidium_benchmark")
                || FabricLoader.getInstance().isModLoaded("rubidium_client_benchmark");
        if((mixin.endsWith("NoiseGeneratorMixin") || mixin.endsWith("MinecraftMixin")) && !harness) return false;
        boolean upload=mixin.endsWith("LevelRendererMixin") || mixin.endsWith("SectionRenderDispatcherAccessor");
        if(upload && (!harness || !Boolean.getBoolean("rubidium.bench.experimentalUploads"))) return false;
        if(upload && FabricLoader.getInstance().isModLoaded("sodium")) return false;
        if((mixin.endsWith("NoiseGeneratorMixin") || mixin.endsWith("BeardifierMixin")) && FabricLoader.getInstance().isModLoaded("c2me")) {
            org.slf4j.LoggerFactory.getLogger("Rubidium Flow").info("C2ME detected: conflicting generation hook disabled ({})",mixin);
            return false;
        }
        // This switch exists only when the separate development harness is installed.
        if((mixin.endsWith("BeardifierMixin") || upload) && (FabricLoader.getInstance().isModLoaded("rubidium_benchmark")
                || FabricLoader.getInstance().isModLoaded("rubidium_client_benchmark")))
            return !System.getProperty("rubidium.bench.mode","observe").equals("observe");
        return true;
    }
    public void onLoad(String mixinPackage) { }
    public String getRefMapperConfig() { return null; }
    public void acceptTargets(Set<String> mine,Set<String> others) { }
    public List<String> getMixins() { return null; }
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) { }
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) { }
}

