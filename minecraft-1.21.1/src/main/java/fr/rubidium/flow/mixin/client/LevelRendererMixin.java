package fr.rubidium.flow.mixin.client;

import fr.rubidium.flow.core.BudgetedUploads;
import java.util.Queue;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Unique private static final long RUBIDIUM_UPLOAD_BUDGET_NANOS = 2_000_000L;
    @Unique private static final int RUBIDIUM_UPLOAD_JOB_LIMIT = 64;

    @Redirect(
            method = "compileSections",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadAllPendingUploads()V"))
    private void rubidium$budgetUploads(SectionRenderDispatcher dispatcher) {
        Queue<Runnable> uploads = ((SectionRenderDispatcherAccessor) dispatcher).rubidium$getToUpload();
        BudgetedUploads.drain(
                uploads,
                System::nanoTime,
                RUBIDIUM_UPLOAD_BUDGET_NANOS,
                RUBIDIUM_UPLOAD_JOB_LIMIT);
    }
}
