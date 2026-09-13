package fr.rubidium.flow.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

class BeardifierMixinTest {
    private static final Method GET_BURY_CONTRIBUTION = method();

    @Test
    void matchesVanillaBitsInsideRadius() {
        for (double[] point : new double[][] {
                {0.0, 0.0, 0.0},
                {1.0, 2.0, 3.0},
                {Math.nextDown(6.0), 0.0, 0.0},
                {3.0, 4.0, Math.nextDown(Math.sqrt(11.0))},
                {Double.NaN, 1.0, 2.0}
        }) {
            double expected = Mth.clampedMap(Mth.length(point[0], point[1], point[2]), 0.0, 6.0, 1.0, 0.0);
            double actual = invoke(point[0], point[1], point[2]);
            assertEquals(
                    Double.doubleToRawLongBits(expected),
                    Double.doubleToRawLongBits(actual),
                    () -> "mismatch at " + java.util.Arrays.toString(point));
        }
    }

    @Test
    void returnsPositiveZeroAtAndOutsideRadius() {
        for (double[] point : new double[][] {
                {6.0, 0.0, 0.0},
                {Math.nextUp(6.0), 0.0, 0.0},
                {6.0, 6.0, 6.0},
                {Double.POSITIVE_INFINITY, 0.0, 0.0},
                {Double.NEGATIVE_INFINITY, 0.0, 0.0}
        }) {
            assertEquals(
                    Double.doubleToRawLongBits(0.0),
                    Double.doubleToRawLongBits(invoke(point[0], point[1], point[2])),
                    () -> "result was not positive zero at " + java.util.Arrays.toString(point));
        }
    }

    private static Method method() {
        try {
            Method method = BeardifierMixin.class.getDeclaredMethod(
                    "getBuryContribution", double.class, double.class, double.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static double invoke(double x, double y, double z) {
        try {
            return (double) GET_BURY_CONTRIBUTION.invoke(null, x, y, z);
        } catch (IllegalAccessException failure) {
            throw new AssertionError(failure);
        } catch (InvocationTargetException failure) {
            throw new AssertionError(failure.getCause());
        }
    }
}
