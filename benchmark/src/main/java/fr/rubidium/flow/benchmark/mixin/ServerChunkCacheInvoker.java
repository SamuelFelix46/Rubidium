package fr.rubidium.flow.benchmark.mixin;

import net.minecraft.server.level.*;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.concurrent.CompletableFuture;

/** Harness only: the public method blocks when called on the server thread. */
@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheInvoker {
    @Invoker("getChunkFutureMainThread")
    CompletableFuture<ChunkResult<ChunkAccess>> rubidium$requestAsync(int x,int z,ChunkStatus status,boolean create);
}
