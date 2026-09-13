package fr.rubidium.flow.client;

import fr.rubidium.flow.core.BudgetedUploads;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Preserves the 1.21.11 dispatcher ordering: upload work first, retired-mesh closing second. */
public final class SectionUploadDrain {
    private static final long UPLOAD_BUDGET_NANOS = 2_000_000L;
    private static final int UPLOAD_JOB_LIMIT = 64;

    private SectionUploadDrain() {
    }

    public static <T> void drain(
            Queue<Runnable> uploads,
            Queue<T> toClose,
            Consumer<? super T> close,
            LongSupplier clock) {
        Objects.requireNonNull(toClose, "toClose");
        Objects.requireNonNull(close, "close");

        BudgetedUploads.drain(uploads, clock, UPLOAD_BUDGET_NANOS, UPLOAD_JOB_LIMIT);

        T retired;
        while ((retired = toClose.poll()) != null) {
            close.accept(retired);
        }
    }
}
