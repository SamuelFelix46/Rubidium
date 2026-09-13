package fr.rubidium.flow.clientbench.mixin;
import java.util.Queue;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(SectionRenderDispatcher.class)
public interface QueueAccessor {
 @Accessor("toUpload") Queue<Runnable> rubidium$benchUploads();
 @Accessor("toClose") Queue<?> rubidium$benchCloses();
}
