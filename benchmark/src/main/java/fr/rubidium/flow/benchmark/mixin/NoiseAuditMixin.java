package fr.rubidium.flow.benchmark.mixin;

import fr.rubidium.flow.benchmark.NoiseAudit;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseAuditMixin {
    @Inject(method="fillFromNoise",at=@At("RETURN"),cancellable=true)
    private void rubidium$audit(CallbackInfoReturnable<CompletableFuture<ChunkAccess>> result) {
        if(NoiseAudit.ENABLED) result.setReturnValue(result.getReturnValue().thenApply(chunk -> {
            NoiseAudit.capture(chunk);
            return chunk;
        }));
    }
}
