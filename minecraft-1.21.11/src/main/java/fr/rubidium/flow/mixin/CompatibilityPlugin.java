package fr.rubidium.flow.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class CompatibilityPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if(FabricLoader.getInstance().isModLoaded("rubidium_client_benchmark12111")
                && System.getProperty("rubidium.bench.mode","observe").equals("observe")) return false;
        if (mixinClassName.endsWith("BeardifierMixin")
                && FabricLoader.getInstance().isModLoaded("c2me")) {
            return false;
        }

        boolean uploadMixin = mixinClassName.endsWith("LevelRendererMixin")
                || mixinClassName.endsWith("SectionRenderDispatcherAccessor");
        if(uploadMixin && (!FabricLoader.getInstance().isModLoaded("rubidium_client_benchmark12111")
                || !Boolean.getBoolean("rubidium.bench.experimentalUploads"))) return false;
        return !uploadMixin || !FabricLoader.getInstance().isModLoaded("sodium");
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }
}

