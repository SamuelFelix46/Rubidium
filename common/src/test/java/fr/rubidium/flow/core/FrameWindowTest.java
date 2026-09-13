package fr.rubidium.flow.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FrameWindowTest {
    @Test
    void rejectsCapacityBelowTwo() {
        assertThrows(IllegalArgumentException.class, () -> new FrameWindow(1));
        assertThrows(IllegalArgumentException.class, () -> new FrameWindow(0));
    }

    @Test
    void reportsLiteralFrameStatistics() {
        FrameWindow window = new FrameWindow(4);
        window.record(1_000_000L);
        window.record(2_000_000L);
        window.record(3_000_000L);
        window.record(4_000_000L);

        FrameWindow.Snapshot snapshot = window.snapshot();

        assertEquals(4, snapshot.count());
        assertEquals(2.5, snapshot.meanMs(), 0.000_001);
        assertEquals(2.5, snapshot.medianMs(), 0.000_001);
        assertEquals(4.0, snapshot.p95Ms(), 0.000_001);
        assertEquals(4.0, snapshot.p99Ms(), 0.000_001);
        assertEquals(4.0, snapshot.maxMs(), 0.000_001);
        assertEquals(250.0, snapshot.onePercentLowFps(), 0.000_001);
        assertEquals(250.0, snapshot.pointOnePercentLowFps(), 0.000_001);
    }

    @Test
    void overwritesOldestFrameAtCapacity() {
        FrameWindow window = new FrameWindow(4);
        for (long millis = 1; millis <= 5; millis++) {
            window.record(millis * 1_000_000L);
        }

        FrameWindow.Snapshot snapshot = window.snapshot();

        assertEquals(4, snapshot.count());
        assertEquals(3.5, snapshot.meanMs(), 0.000_001);
        assertEquals(3.5, snapshot.medianMs(), 0.000_001);
        assertEquals(5.0, snapshot.maxMs(), 0.000_001);
    }

    @Test
    void ignoresNonpositiveFramesAndClearResetsAllMetrics() {
        FrameWindow window = new FrameWindow(4);
        window.record(0L);
        window.record(-1L);
        assertEquals(0, window.snapshot().count());

        window.record(2_000_000L);
        window.clear();

        assertEquals(new FrameWindow.Snapshot(0, 0, 0, 0, 0, 0, 0, 0), window.snapshot());
    }

    @Test
    void concurrentRecordingAndSnapshotsRemainConsistent() throws InterruptedException {
        FrameWindow window = new FrameWindow(64);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 20_000; i++) {
                    window.record((i % 2 + 1) * 1_000_000L);
                }
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });

        writer.start();
        start.countDown();
        assertDoesNotThrow(() -> {
            while (writer.isAlive()) {
                FrameWindow.Snapshot snapshot = window.snapshot();
                assertTrue(snapshot.count() >= 0 && snapshot.count() <= 64);
                if (snapshot.count() > 0) {
                    assertTrue(snapshot.meanMs() >= 1.0 && snapshot.meanMs() <= 2.0);
                    assertTrue(snapshot.medianMs() >= 1.0 && snapshot.medianMs() <= 2.0);
                    assertTrue(snapshot.maxMs() >= 1.0 && snapshot.maxMs() <= 2.0);
                }
            }
        });
        writer.join();

        assertEquals(null, failure.get());
        assertEquals(64, window.snapshot().count());
    }
}
