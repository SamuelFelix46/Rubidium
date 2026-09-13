package fr.rubidium.flow.mixin.client;

import fr.rubidium.flow.RuntimeMonitor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Unique private long rubidium$previous;
    @Unique private int rubidium$fpsLimit;
    @Unique private boolean rubidium$vsync;
    @Inject(method="runTick",at=@At("HEAD"))
    private void rubidium$frame(boolean render, CallbackInfo ci) {
        Minecraft client=(Minecraft)(Object)this;
        int limit=client.options.framerateLimit().get();
        boolean vsync=client.options.enableVsync().get();
        if(!render || client.level==null || client.screen!=null || client.isPaused() || !client.isWindowActive()
                || limit!=rubidium$fpsLimit || vsync!=rubidium$vsync) {
            if(rubidium$previous!=0) RuntimeMonitor.clearFrames();
            rubidium$previous=0; rubidium$fpsLimit=limit; rubidium$vsync=vsync; return;
        }
        long now=System.nanoTime();
        if(rubidium$previous!=0) RuntimeMonitor.recordFrame(now-rubidium$previous);
        rubidium$previous=now;
    }
}
