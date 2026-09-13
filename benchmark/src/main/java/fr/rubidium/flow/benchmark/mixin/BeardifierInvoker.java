package fr.rubidium.flow.benchmark.mixin;
import net.minecraft.world.level.levelgen.Beardifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Beardifier.class)
public interface BeardifierInvoker {
    @Invoker("getBuryContribution")
    static double rubidium$bury(double x,double y,double z) { throw new AssertionError(); }
}
