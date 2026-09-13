package fr.rubidium.flow.benchmark;
import fr.rubidium.flow.benchmark.mixin.BeardifierInvoker;
import net.minecraft.util.Mth;
import java.util.Random;

/** Compare the transformed Minecraft method to its original arithmetic, including boundary values. */
final class MathAudit {
    static long verify() {
        long checked=0;
        for(int x=-32;x<=32;x++) for(int y=-32;y<=32;y++) for(int z=-32;z<=32;z++) {
            check(x*.5,y*.5,z*.5); checked++;
        }
        double[] edges={0.0,-0.0,Double.MIN_VALUE,Double.MAX_VALUE,Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,Double.NaN,6,Math.nextDown(6.0),Math.nextUp(6.0),-6};
        for(double x:edges) for(double y:edges) for(double z:edges) { check(x,y,z); checked++; }
        Random random=new Random(93741);
        for(int i=0;i<100_000;i++) {
            check(Double.longBitsToDouble(random.nextLong()),Double.longBitsToDouble(random.nextLong()),
                    Double.longBitsToDouble(random.nextLong())); checked++;
        }
        return checked;
    }
    private static void check(double x,double y,double z) {
        double expected=Mth.clampedMap(Mth.length(x,y,z),0,6,1,0);
        double actual=BeardifierInvoker.rubidium$bury(x,y,z);
        if(Double.doubleToLongBits(expected)!=Double.doubleToLongBits(actual))
            throw new AssertionError("Bury arithmetic differs at "+x+","+y+","+z+": "+expected+" vs "+actual);
    }
}
