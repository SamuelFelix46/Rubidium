package fr.rubidium.flow.mixin.client;
import fr.rubidium.flow.client.SectionUploadDrain;

import java.util.Queue;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Redirect(
            method = "compileSections",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadAllPendingUploads()V"))
    private void rubidium$budgetUploads(SectionRenderDispatcher dispatcher) {
        SectionRenderDispatcherAccessor accessor = (SectionRenderDispatcherAccessor) dispatcher;
        Queue<Runnable> uploads = accessor.rubidium$getToUpload();
        Queue<SectionMesh> retiredMeshes = accessor.rubidium$getToClose();
        SectionUploadDrain.drain(uploads, retiredMeshes, SectionMesh::close, System::nanoTime);
    }
}
