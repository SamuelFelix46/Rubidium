package fr.rubidium.audit.mixin;

import net.minecraft.world.level.levelgen.Beardifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Beardifier.class)
public interface BeardifierInvoker {
    @Invoker("getBuryContribution")
    static float contribution(float x, float y, float z) {
        throw new AssertionError();
    }
}
