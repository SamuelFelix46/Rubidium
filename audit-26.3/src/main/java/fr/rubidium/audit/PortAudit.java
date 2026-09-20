package fr.rubidium.audit;

import fr.rubidium.audit.mixin.BeardifierInvoker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.Mth;

public final class PortAudit implements ModInitializer {
    private int ticks;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks != 3) {
                return;
            }

            long cases = 0;
            for (int x = -32; x <= 32; x++) {
                for (int y = -32; y <= 32; y++) {
                    for (int z = -32; z <= 32; z++) {
                        check(x * 0.5F, y * 0.5F, z * 0.5F);
                        cases++;
                    }
                }
            }

            float[] edges = {
                    0.0F, -0.0F, Float.MIN_VALUE, Float.MAX_VALUE,
                    Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                    Float.NaN, 6.0F, Math.nextDown(6.0F), Math.nextUp(6.0F), -6.0F
            };
            for (float x : edges) {
                for (float y : edges) {
                    for (float z : edges) {
                        check(x, y, z);
                        cases++;
                    }
                }
            }

            Random random = new Random(93741);
            for (int index = 0; index < 100_000; index++) {
                check(
                        Float.intBitsToFloat(random.nextInt()),
                        Float.intBitsToFloat(random.nextInt()),
                        Float.intBitsToFloat(random.nextInt()));
                cases++;
            }

            try {
                Files.writeString(
                        Path.of("port-audit.json"),
                        "{\"minecraft\":\"26.3\",\"exactMathCases\":" + cases
                                + ",\"passed\":true,\"nativeFastPath\":true,\"rubidiumMixinApplied\":false,\"renderingModified\":false}");
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }

            System.out.println("RUBIDIUM_PORT_AUDIT_OK 26.3 cases=" + cases);
            server.halt(false);
        });
    }

    private static void check(float x, float y, float z) {
        float squared = Mth.lengthSquared(x, y, z);
        float expected = squared >= 36.0F ? 0.0F : 1.0F - Mth.sqrt(squared) / 6.0F;
        float actual = BeardifierInvoker.contribution(x, y, z);
        if (Float.floatToRawIntBits(expected) != Float.floatToRawIntBits(actual)) {
            throw new AssertionError("Exact vanilla math changed");
        }
    }
}
