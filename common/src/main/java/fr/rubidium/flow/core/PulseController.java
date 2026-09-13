package fr.rubidium.flow.core;

public final class PulseController {
    private static final long DECREASE_COOLDOWN_NANOS = 500_000_000L;
    private static final long RECOVERY_QUIET_NANOS = 5_000_000_000L;
    private static final long CHANGE_COOLDOWN_NANOS = 2_000_000_000L;

    private final int ceiling;
    private volatile int limit;
    private boolean initialized;
    private boolean hasDecreased;
    private long lastOverloadNanos;
    private long lastChangeNanos;
    private long lastDecreaseNanos;

    public PulseController(int processors) {
        long availableProcessors = Math.max(1, processors);
        long reservedProcessors = Math.max(1, (availableProcessors + 3L) / 4L);
        this.ceiling = (int) Math.min(16L, Math.max(1L, availableProcessors - reservedProcessors));
        this.limit = Math.min(2, ceiling);
    }

    public int update(
            long nowNanos,
            double cpuLoad,
            double heapRatio,
            double tickMs,
            double frameMs,
            double frameBaselineMs,
            int queued) {
        boolean cpuKnown = knownRatio(cpuLoad);
        boolean heapKnown = knownRatio(heapRatio);
        boolean tickKnown = knownDuration(tickMs);
        boolean frameKnown = knownDuration(frameMs);
        boolean baselineKnown = knownDuration(frameBaselineMs);

        boolean overloaded = (cpuKnown && cpuLoad >= 0.93)
                || (heapKnown && heapRatio >= 0.88)
                || (tickKnown && tickMs >= 45.0)
                || (cpuKnown && cpuLoad >= 0.65 && frameKnown && baselineKnown
                        && frameMs >= Math.max(25.0, frameBaselineMs * 1.7));

        if (!initialized) {
            initialized = true;
            lastOverloadNanos = nowNanos;
            lastChangeNanos = nowNanos;
        }

        if (overloaded) {
            lastOverloadNanos = nowNanos;
            if (limit > 1
                    && (!hasDecreased
                            || elapsedAtLeast(nowNanos, lastDecreaseNanos, DECREASE_COOLDOWN_NANOS))) {
                limit = Math.max(1, limit / 2);
                hasDecreased = true;
                lastDecreaseNanos = nowNanos;
                lastChangeNanos = nowNanos;
            }
            return limit;
        }

        boolean anyResourceKnown = cpuKnown || heapKnown || tickKnown || frameKnown;
        boolean healthy = anyResourceKnown
                && (!cpuKnown || cpuLoad < 0.80)
                && (!heapKnown || heapRatio < 0.78)
                && (!tickKnown || tickMs < 35.0)
                && (!(cpuKnown && cpuLoad >= 0.65 && frameKnown && baselineKnown)
                        || frameMs < Math.max(20.0, frameBaselineMs * 1.25));

        if (limit < ceiling
                && queued > 0
                && healthy
                && elapsedAtLeast(nowNanos, lastOverloadNanos, RECOVERY_QUIET_NANOS)
                && elapsedAtLeast(nowNanos, lastChangeNanos, CHANGE_COOLDOWN_NANOS)) {
            limit++;
            lastChangeNanos = nowNanos;
        }
        return limit;
    }

    public int limit() {
        return limit;
    }

    public int ceiling() {
        return ceiling;
    }

    private static boolean knownRatio(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private static boolean knownDuration(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    private static boolean elapsedAtLeast(long nowNanos, long thenNanos, long durationNanos) {
        return nowNanos - thenNanos >= durationNanos;
    }
}
