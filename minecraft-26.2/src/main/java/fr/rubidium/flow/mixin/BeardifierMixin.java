package fr.rubidium.flow.mixin;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Beardifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
@Mixin(Beardifier.class)
abstract class BeardifierMixin {
    /** @author Rubidium contributors
     * @reason Preserve exact vanilla zero outside radius six without square root/division. */
    @Overwrite
    private static double getBuryContribution(double x,double y,double z) {
        double squared=Mth.lengthSquared(x,y,z);
        if(squared>=36.0) return 0.0;
        return Mth.clampedMap(Math.sqrt(squared),0.0,6.0,1.0,0.0);
    }
}
