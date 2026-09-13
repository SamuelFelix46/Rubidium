package fr.rubidium.flow.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class IntervalTelemetryTest {
    @Test
    void drainReportsLiteralCountMeanMaximumAndLatestTimestamp() {
        IntervalTelemetry telemetry = new IntervalTelemetry();
        telemetry.record(1_000_000L, 10L);
        telemetry.record(2_000_000L, 20L);
        telemetry.record(6_000_000L, 30L);

        assertEquals(new IntervalTelemetry.Sample(3, 3.0, 6.0, 30L), telemetry.drain());
    }

    @Test
    void ignoresInvalidDurationsAndEmptyDrainRetainsLastTimestamp() {
        IntervalTelemetry telemetry = new IntervalTelemetry();
        telemetry.record(2_000_000L, -50L);
        assertEquals(1, telemetry.drain().count());

        telemetry.record(0L, 100L);
        telemetry.record(-1L, 200L);

        assertEquals(new IntervalTelemetry.Sample(0, 0.0, 0.0, -50L), telemetry.drain());
    }

    @Test
    void sampleFreshnessUsesRecordedTimestampAndTwoSecondBoundary() {
        IntervalTelemetry.Sample sample = new IntervalTelemetry.Sample(1, 10.0, 10.0, -5_000_000_000L);
        IntervalTelemetry.Sample empty = new IntervalTelemetry.Sample(0, 0.0, 0.0, -5_000_000_000L);

        assertTrue(sample.isFreshAt(-3_000_000_000L, 2_000_000_000L));
        assertTrue(!sample.isFreshAt(-2_999_999_999L, 2_000_000_000L));
        assertTrue(!empty.isFreshAt(-5_000_000_000L, 2_000_000_000L));
    }

    @Test
    void concurrentRecordAndDrainPreserveEveryCount() throws InterruptedException {
        IntervalTelemetry telemetry = new IntervalTelemetry();
        int writers = 4;
        int samplesPerWriter = 10_000;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int writer = 0; writer < writers; writer++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < samplesPerWriter; i++) {
                        telemetry.record(1_000_000L, i);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(thread);
            thread.start();
        }

        start.countDown();
        long drained = 0;
        while (threads.stream().anyMatch(Thread::isAlive)) {
            drained += telemetry.drain().count();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        drained += telemetry.drain().count();

        assertEquals((long) writers * samplesPerWriter, drained);
        assertTrue(telemetry.drain().count() == 0);
    }
}
