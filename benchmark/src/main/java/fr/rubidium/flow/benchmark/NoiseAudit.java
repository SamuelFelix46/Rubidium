package fr.rubidium.flow.benchmark;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Expensive, opt-in integrity run; its timings must never be used as performance results. */
public final class NoiseAudit {
    public static final boolean ENABLED=Boolean.getBoolean("rubidium.bench.noiseAudit");
    private static final int SIDE=Integer.getInteger("rubidium.bench.side",8);
    private static final Map<String,Map<String,String>> HASHES=new ConcurrentHashMap<>();
    private NoiseAudit() { }
    public static void capture(ChunkAccess chunk) {
        int cx=chunk.getPos().x,cz=chunk.getPos().z;
        if(cx<1000 || cz<1000 || cx>=1000+SIDE || cz>=1000+SIDE) return;
        try {
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            Map<BlockState,byte[]> names=new HashMap<>();
            var pos=new BlockPos.MutableBlockPos();
            for(int y=chunk.getMinBuildHeight();y<chunk.getMaxBuildHeight();y++)
                for(int z=0;z<16;z++) {
                    for(int x=0;x<16;x++) {
                        BlockState state=chunk.getBlockState(pos.set(cx*16+x,y,cz*16+z));
                        hash.update(names.computeIfAbsent(state,s -> (s.toString()+"\n").getBytes(StandardCharsets.UTF_8)));
                    }
                }
            String blocks=HexFormat.of().formatHex(hash.digest());
            for(int y=chunk.getMinBuildHeight()/4;y<chunk.getMaxBuildHeight()/4;y++)
                for(int z=0;z<4;z++) for(int x=0;x<4;x++) {
                    String biome=chunk.getNoiseBiome(cx*4+x,y,cz*4+z).unwrapKey().orElseThrow().location()+"\n";
                    hash.update(biome.getBytes(StandardCharsets.UTF_8));
                }
            String key=cx+","+cz;
            if(HASHES.putIfAbsent(key,Map.of("blocks",blocks,"biomes",HexFormat.of().formatHex(hash.digest())))!=null)
                throw new IllegalStateException("Duplicate audited noise chunk: "+key);
        } catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static Map<String,Map<String,String>> snapshot() { return new TreeMap<>(HASHES); }
    public static void requireComplete() {
        Set<String> expected=new TreeSet<>();
        for(int z=1000;z<1000+SIDE;z++) for(int x=1000;x<1000+SIDE;x++) expected.add(x+","+z);
        if(!HASHES.keySet().equals(expected)) throw new IllegalStateException("Incomplete NOISE coverage: "+HASHES.size()+" / "+expected.size());
    }
}
