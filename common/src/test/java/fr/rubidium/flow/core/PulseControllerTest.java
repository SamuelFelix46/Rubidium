package fr.rubidium.flow.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PulseControllerTest {
    private static final long SECOND = 1_000_000_000L;

    @ParameterizedTest
    @MethodSource("processorBounds")
    void derivesBoundedCeilingAndInitialLimit(int processors, int expectedCeiling, int expectedInitial) {
        PulseController controller = new PulseController(processors);

        assertEquals(expectedCeiling, controller.ceiling());
        assertEquals(expectedInitial, controller.limit());
    }

    private static Stream<Arguments> processorBounds() {
        return Stream.of(
                Arguments.of(1, 1, 1),
                Arguments.of(2, 1, 1),
                Arguments.of(4, 3, 2),
                Arguments.of(8, 6, 2),
                Arguments.of(32, 16, 2),
                Arguments.of(0, 1, 1));
    }

    @Test
    void overloadHalvesImmediatelyOnFirstUpdate() {
        PulseController controller = new PulseController(32);

        int limit = controller.update(0L, 0.93, -1.0, -1.0, -1.0, -1.0, 10);

        assertEquals(1, limit);
        assertEquals(1, controller.limit());
    }

    @Test
    void everyOverloadSignalCanThrottleWithoutOtherMeasurements() {
        assertImmediateThrottle(0.93, -1.0, -1.0, -1.0, -1.0);
        assertImmediateThrottle(-1.0, 0.88, -1.0, -1.0, -1.0);
        assertImmediateThrottle(-1.0, -1.0, 45.0, -1.0, -1.0);
    }

    @Test
    void frameSpikeRequiresKnownCpuContentionToThrottle() {
        PulseController gpuBound = new PulseController(8);
        PulseController cpuContended = new PulseController(8);

        assertEquals(2, gpuBound.update(0L, 0.2, -1.0, -1.0, 50.0, 8.0, 1));
        assertEquals(1, cpuContended.update(0L, 0.8, -1.0, -1.0, 50.0, 8.0, 1));
    }

    @Test
    void lowCpuFrameSpikeAllowsRecoveryWhileCpuContendedSpikeHolds() {
        PulseController gpuBound = new PulseController(8);
        PulseController cpuContended = new PulseController(8);
        assertEquals(1, gpuBound.update(0L, 0.2, 0.88, 10.0, 50.0, 8.0, 5));
        assertEquals(1, cpuContended.update(0L, 0.7, 0.88, 10.0, 50.0, 8.0, 5));

        assertEquals(2, gpuBound.update(6 * SECOND, 0.2, 0.5, 10.0, 50.0, 8.0, 5));
        assertEquals(1, cpuContended.update(6 * SECOND, 0.7, 0.5, 10.0, 50.0, 8.0, 5));
    }

    @Test
    void ratiosAboveOneAndInfinityAreUnavailable() {
        PulseController controller = new PulseController(8);

        assertEquals(2, controller.update(0L, 1.01, Double.POSITIVE_INFINITY,
                -1.0, -1.0, -1.0, 2));
        assertEquals(2, controller.update(6 * SECOND, Double.POSITIVE_INFINITY, 1.01,
                -1.0, -1.0, -1.0, 2));
    }

    private static void assertImmediateThrottle(
            double cpu, double heap, double tick, double frame, double baseline) {
        PulseController controller = new PulseController(8);
        assertEquals(1, controller.update(0L, cpu, heap, tick, frame, baseline, 1));
    }

    @Test
    void repeatedOverloadWaitsFiveHundredMillisecondsBetweenDecreases() {
        PulseController controller = recoveredController(32, 0L);
        assertEquals(16, controller.limit());

        assertEquals(8, controller.update(40 * SECOND, 0.95, 0.5, 10, 10, 10, 10));
        assertEquals(8, controller.update(40 * SECOND + 499_999_999L, 0.95, 0.5, 10, 10, 10, 10));
        assertEquals(4, controller.update(40 * SECOND + 500_000_000L, 0.95, 0.5, 10, 10, 10, 10));
    }

    @Test
    void recoveryRequiresQuietPeriodChangeCooldownQueueAndHealthyKnownSamples() {
        PulseController controller = new PulseController(8);
        assertEquals(1, controller.update(0L, 0.95, 0.5, 10, 10, 10, 5));

        assertEquals(1, controller.update(4 * SECOND + 999_999_999L, 0.5, 0.5, 10, 10, 10, 5));
        assertEquals(1, controller.update(5 * SECOND, 0.5, 0.5, 10, 10, 10, 0));
        assertEquals(1, controller.update(6 * SECOND, 0.80, 0.5, 10, 10, 10, 5));
        assertEquals(2, controller.update(7 * SECOND, 0.79, 0.77, 34.9, 19.9, 16.0, 5));
        assertEquals(2, controller.update(8 * SECOND + 999_999_999L, 0.5, 0.5, 10, 10, 10, 5));
        assertEquals(3, controller.update(9 * SECOND, 0.5, 0.5, 10, 10, 10, 5));
    }

    @Test
    void unknownAndNanSamplesNeitherThrottleNorPermitRecoveryWhenAllAreMissing() {
        PulseController controller = new PulseController(8);

        assertEquals(2, controller.update(-10 * SECOND, Double.NaN, -1.0, Double.NaN,
                Double.POSITIVE_INFINITY, 10.0, 4));
        assertEquals(2, controller.update(-4 * SECOND, -1.0, Double.NaN, 0.0,
                -1.0, Double.NaN, 4));
        assertEquals(3, controller.update(-2 * SECOND, 0.5, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, 4));
    }

    @Test
    void negativeQueueIsTreatedAsEmpty() {
        PulseController controller = new PulseController(8);

        controller.update(0L, 0.5, 0.5, 10, 10, 10, 1);
        assertEquals(2, controller.update(6 * SECOND, 0.5, 0.5, 10, 10, 10, -1));
    }

    @Test
    void longHealthyRunRecoversOneStepAtATimeWithoutExceedingCeiling() {
        PulseController controller = new PulseController(8);
        long start = -100 * SECOND;
        controller.update(start, 0.95, 0.5, 10, 10, 10, 10);

        for (int i = 1; i <= 100; i++) {
            controller.update(start + i * SECOND, 0.4, 0.4, 10, 10, 10, 10);
        }

        assertEquals(6, controller.limit());
        assertEquals(6, controller.ceiling());
    }

    private static PulseController recoveredController(int processors, long start) {
        PulseController controller = new PulseController(processors);
        controller.update(start, 0.5, 0.5, 10, 10, 10, 10);
        for (int i = 1; i <= 31; i++) {
            controller.update(start + i * SECOND, 0.5, 0.5, 10, 10, 10, 10);
        }
        return controller;
    }
}
