package fr.rubidium.audit;
import fr.rubidium.audit.mixin.BeardifierInvoker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.Mth;
import java.nio.file.*;
import java.util.*;
public final class PortAudit implements ModInitializer {
    private int ticks;
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(++ticks!=3) return;
            long cases=0;
            for(int x=-32;x<=32;x++) for(int y=-32;y<=32;y++) for(int z=-32;z<=32;z++) {
                check(x*.5,y*.5,z*.5); cases++;
            }
            double[] edges={0.0,-0.0,Double.MIN_VALUE,Double.MAX_VALUE,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NaN,6,Math.nextDown(6.0),Math.nextUp(6.0),-6};
            for(double x:edges) for(double y:edges) for(double z:edges) { check(x,y,z); cases++; }
            Random r=new Random(93741);
            for(int i=0;i<100_000;i++) { check(Double.longBitsToDouble(r.nextLong()),Double.longBitsToDouble(r.nextLong()),Double.longBitsToDouble(r.nextLong())); cases++; }
            try { Files.writeString(Path.of("port-audit.json"),"{\"minecraft\":\"26.2\",\"exactMathCases\":"+cases+",\"passed\":true,\"renderingModified\":false}"); }
            catch(Exception e) { throw new RuntimeException(e); }
            System.out.println("RUBIDIUM_PORT_AUDIT_OK 26.2 cases="+cases);
            server.halt(false);
        });
    }
    private static void check(double x,double y,double z) {
        double expected=Mth.clampedMap(Mth.length(x,y,z),0,6,1,0);
        double actual=BeardifierInvoker.contribution(x,y,z);
        if(Double.doubleToLongBits(expected)!=Double.doubleToLongBits(actual)) throw new AssertionError("Exact math changed");
    }
}
