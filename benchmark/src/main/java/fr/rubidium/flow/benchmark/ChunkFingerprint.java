package fr.rubidium.flow.benchmark;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.block.state.BlockState;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Canonical terrain fingerprint, calculated AFTER the timed generation window. */
final class ChunkFingerprint {
    private final IdentityHashMap<BlockState,byte[]> blockNames=new IdentityHashMap<>();
    private final Map<String,byte[]> biomeNames=new HashMap<>();
    Map<String,String> hash(ServerLevel world,ChunkAccess chunk) {
        try {
            Map<String,String> result=new TreeMap<>();
            MessageDigest blocks=MessageDigest.getInstance("SHA-256");
            var pos=new BlockPos.MutableBlockPos();
            int minX=chunk.getPos().getMinBlockX(), minZ=chunk.getPos().getMinBlockZ();
            for(int y=chunk.getMinBuildHeight();y<chunk.getMaxBuildHeight();y++)
                for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
                    BlockState state=chunk.getBlockState(pos.set(minX+x,y,minZ+z));
                    blocks.update(blockNames.computeIfAbsent(state,s -> (s.toString()+"\n").getBytes(StandardCharsets.UTF_8)));
                }
            result.put("blocks",HexFormat.of().formatHex(blocks.digest()));
            MessageDigest biomes=MessageDigest.getInstance("SHA-256");
            for(int y=chunk.getMinBuildHeight()/4;y<chunk.getMaxBuildHeight()/4;y++)
                for(int z=0;z<4;z++) for(int x=0;x<4;x++) {
                    String name=chunk.getNoiseBiome(minX/4+x,y,minZ/4+z).unwrapKey().orElseThrow().location().toString();
                    biomes.update(biomeNames.computeIfAbsent(name,s -> (s+"\n").getBytes(StandardCharsets.UTF_8)));
                }
            result.put("biomes",HexFormat.of().formatHex(biomes.digest()));
            CompoundTag tag=ChunkSerializer.write(world,chunk);
            result.put("heightmaps",digestTag(tag.getCompound("Heightmaps")));
            CompoundTag structures=tag.getCompound("structures").copy();
            CompoundTag refs=structures.getCompound("References");
            for(String key:refs.getAllKeys()) {
                long[] values=refs.getLongArray(key); Arrays.sort(values); refs.putLongArray(key,values);
            }
            result.put("structures",digestTag(structures));
            return result;
        } catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String digestTag(Tag tag) throws NoSuchAlgorithmException {
        MessageDigest digest=MessageDigest.getInstance("SHA-256"); canonical(tag,digest);
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void canonical(Tag tag,MessageDigest digest) {
        digest.update(tag.getId());
        if(tag instanceof CompoundTag compound) {
            for(String key:new TreeSet<>(compound.getAllKeys())) {
                byte[] bytes=key.getBytes(StandardCharsets.UTF_8);
                digest.update((byte)(bytes.length>>>8)); digest.update((byte)bytes.length); digest.update(bytes);
                canonical(Objects.requireNonNull(compound.get(key)),digest);
            }
            digest.update((byte)0);
        } else if(tag instanceof ListTag list) {
            for(Tag child:list) canonical(child,digest);
            digest.update((byte)0);
        } else {
            byte[] bytes=tag.toString().getBytes(StandardCharsets.UTF_8);
            for(int shift=24;shift>=0;shift-=8) digest.update((byte)(bytes.length>>>shift));
            digest.update(bytes);
        }
    }
}
