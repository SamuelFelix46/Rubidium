package fr.rubidium.flow.core;

public final class IntervalTelemetry {
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0;

    private long count;
    private double totalNanos;
    private long maxNanos;
    private long lastAt;

    public synchronized void record(long durationNanos, long timestampNanos) {
        if (durationNanos <= 0) {
            return;
        }
        count++;
        totalNanos += durationNanos;
        maxNanos = Math.max(maxNanos, durationNanos);
        lastAt = timestampNanos;
    }

    public synchronized Sample drain() {
        Sample sample = count == 0
                ? new Sample(0, 0, 0, lastAt)
                : new Sample(
                        count,
                        totalNanos / count / NANOS_PER_MILLISECOND,
                        maxNanos / NANOS_PER_MILLISECOND,
                        lastAt);
        count = 0;
        totalNanos = 0;
        maxNanos = 0;
        return sample;
    }

    public record Sample(long count, double meanMs, double maxMs, long lastAt) {
        public boolean isFreshAt(long nowNanos, long maxAgeNanos) {
            return count > 0 && maxAgeNanos >= 0 && nowNanos - lastAt <= maxAgeNanos;
        }
    }
}
