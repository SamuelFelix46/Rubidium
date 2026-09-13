package fr.rubidium.flow.mixin;

import fr.rubidium.flow.RuntimeMonitor;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseGeneratorMixin {
    @Redirect(method="fillFromNoise", at=@At(value="INVOKE", target="Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private <T> CompletableFuture<T> rubidium$paceNoise(Supplier<T> original, Executor originalExecutor) {
        return RuntimeMonitor.noise(original,originalExecutor);
    }
}
