package fr.rubidium.flow.core;

import java.util.Arrays;

public final class FrameWindow {
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final long[] frames;
    private int size;
    private int next;

    public FrameWindow(int capacity) {
        if (capacity < 2) {
            throw new IllegalArgumentException("capacity must be at least 2");
        }
        this.frames = new long[capacity];
    }

    public synchronized void record(long frameNanos) {
        if (frameNanos <= 0L) {
            return;
        }
        frames[next] = frameNanos;
        next = (next + 1) % frames.length;
        if (size < frames.length) {
            size++;
        }
    }

    public synchronized void clear() {
        size = 0;
        next = 0;
    }

    public Snapshot snapshot() {
        long[] sorted;
        synchronized (this) {
            if (size == 0) {
                return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0);
            }
            sorted = Arrays.copyOf(frames, size);
        }
        Arrays.sort(sorted);

        double sumNanos = 0.0;
        for (long frame : sorted) {
            sumNanos += frame;
        }
        int count = sorted.length;
        double meanMs = sumNanos / count / NANOS_PER_MILLISECOND;
        double medianMs;
        if ((count & 1) == 0) {
            medianMs = (sorted[count / 2 - 1] / 2.0 + sorted[count / 2] / 2.0)
                    / NANOS_PER_MILLISECOND;
        } else {
            medianMs = sorted[count / 2] / NANOS_PER_MILLISECOND;
        }

        return new Snapshot(
                count,
                meanMs,
                medianMs,
                nearestRankMillis(sorted, 0.95),
                nearestRankMillis(sorted, 0.99),
                sorted[count - 1] / NANOS_PER_MILLISECOND,
                slowestMeanFps(sorted, 0.01),
                slowestMeanFps(sorted, 0.001));
    }

    private static double nearestRankMillis(long[] sorted, double quantile) {
        int rank = (int) Math.ceil(sorted.length * quantile);
        return sorted[rank - 1] / NANOS_PER_MILLISECOND;
    }

    private static double slowestMeanFps(long[] sorted, double fraction) {
        int sampleCount = (int) Math.ceil(sorted.length * fraction);
        double sumNanos = 0.0;
        for (int i = sorted.length - sampleCount; i < sorted.length; i++) {
            sumNanos += sorted[i];
        }
        return NANOS_PER_SECOND / (sumNanos / sampleCount);
    }

    public record Snapshot(
            int count,
            double meanMs,
            double medianMs,
            double p95Ms,
            double p99Ms,
            double maxMs,
            double onePercentLowFps,
            double pointOnePercentLowFps) {
    }
}
