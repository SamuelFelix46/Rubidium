package fr.rubidium.flow.clientbench.mixin;
import fr.rubidium.flow.clientbench.ClientBenchmark;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
abstract class ClientFrameMixin {
    @Inject(method="runTick",at=@At("HEAD"))
    private void rubidium$measure(boolean render,CallbackInfo ci) {
        if(render) ClientBenchmark.frame((Minecraft)(Object)this);
    }
}
