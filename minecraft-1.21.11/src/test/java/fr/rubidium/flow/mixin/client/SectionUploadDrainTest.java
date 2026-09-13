package fr.rubidium.flow.mixin.client;
import fr.rubidium.flow.client.SectionUploadDrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class SectionUploadDrainTest {
    @Test
    void partialUploadFrameFullyClosesAlreadyRetiredMeshes() {
        List<String> events = new ArrayList<>();
        Queue<Runnable> uploads = new ArrayDeque<>();
        uploads.add(() -> events.add("upload-1"));
        uploads.add(() -> events.add("upload-2"));
        Queue<String> closes = new ArrayDeque<>(List.of("mesh-1", "mesh-2"));

        SectionUploadDrain.drain(
                uploads,
                closes,
                mesh -> events.add("close-" + mesh),
                new SequenceClock(0L, 2_000_000L));

        assertEquals(List.of("upload-1", "close-mesh-1", "close-mesh-2"), events);
        assertEquals(1, uploads.size());
        assertEquals(0, closes.size());
    }

    @Test
    void meshRetiredByCompletedUploadClosesInSameFrame() {
        List<String> closed = new ArrayList<>();
        Queue<String> closes = new ArrayDeque<>();
        Queue<Runnable> uploads = new ArrayDeque<>();
        uploads.add(() -> closes.add("old-mesh"));

        SectionUploadDrain.drain(uploads, closes, closed::add, () -> 0L);

        assertEquals(List.of("old-mesh"), closed);
    }

    @Test
    void uploadFailurePreservesVanillaOrderingAndDoesNotStartCloseDrain() {
        Queue<Runnable> uploads = new ArrayDeque<>();
        IllegalStateException expected = new IllegalStateException("upload");
        uploads.add(() -> { throw expected; });
        Queue<String> closes = new ArrayDeque<>(List.of("mesh"));

        IllegalStateException actual = assertThrows(
                IllegalStateException.class,
                () -> SectionUploadDrain.drain(uploads, closes, ignored -> { }, () -> 0L));

        assertEquals(expected, actual);
        assertEquals(List.of("mesh"), List.copyOf(closes));
    }

    @Test
    void closeFailurePropagatesAndLeavesLaterMeshesQueued() {
        Queue<Runnable> uploads = new ArrayDeque<>();
        Queue<String> closes = new ArrayDeque<>(List.of("first", "second"));
        IllegalStateException expected = new IllegalStateException("close");

        IllegalStateException actual = assertThrows(
                IllegalStateException.class,
                () -> SectionUploadDrain.drain(
                        uploads,
                        closes,
                        mesh -> { throw expected; },
                        () -> 0L));

        assertEquals(expected, actual);
        assertEquals(List.of("second"), List.copyOf(closes));
    }

    private static final class SequenceClock implements LongSupplier {
        private final long[] values;
        private int index;

        private SequenceClock(long... values) {
            this.values = values;
        }

        @Override
        public long getAsLong() {
            return values[Math.min(index++, values.length - 1)];
        }
    }
}
