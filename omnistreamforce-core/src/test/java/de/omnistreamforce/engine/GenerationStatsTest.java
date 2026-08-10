package de.omnistreamforce.engine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationStatsTest {

    @Test
    void perTopicStatsCarryAverageLatency() {
        GenerationStats stats = new GenerationStats();
        stats.recordEvent("a-events", false, 10.0, 100);
        stats.recordEvent("a-events", true, 20.0, 100);
        stats.recordEvent("b-events", false, 4.0, 50);

        GenerationStats.Snapshot snapshot = stats.snapshot();
        TopicStats a = snapshot.perTopicStats().get("a-events");
        TopicStats b = snapshot.perTopicStats().get("b-events");

        assertThat(a.totalSent()).isEqualTo(2);
        assertThat(a.totalErrors()).isEqualTo(1);
        assertThat(a.avgLatencyMs()).isEqualTo(15.0);
        assertThat(b.avgLatencyMs()).isEqualTo(4.0);
        assertThat(snapshot.avgLatencyMs()).isCloseTo(11.33, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void zeroLatencyDoesNotDragTheAverageDown() {
        GenerationStats stats = new GenerationStats();
        stats.recordEvent("a-events", false, 0.0, 10);
        stats.recordEvent("a-events", false, 8.0, 10);

        assertThat(stats.snapshot().perTopicStats().get("a-events").avgLatencyMs()).isEqualTo(8.0);
    }

    @Test
    void failuresAreCountedPerTopic() {
        GenerationStats stats = new GenerationStats();
        stats.recordEvent("a-events", false, 5.0, 10);
        stats.recordFailure("a-events");

        TopicStats a = stats.snapshot().perTopicStats().get("a-events");
        assertThat(a.totalSent()).isEqualTo(2);
        assertThat(a.totalAcknowledged()).isEqualTo(1);
        assertThat(a.totalFailed()).isEqualTo(1);
    }

    @Test
    void snapshotIsSafeWhileOtherThreadsRecord() throws Exception {
        GenerationStats stats = new GenerationStats();
        int threads = 8;
        int perThread = 2000;
        CountDownLatch done = new CountDownLatch(threads);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                final int id = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < perThread; i++) {
                            stats.recordEvent("topic-" + (id % 4), i % 5 == 0, 1.0, 10);
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            // Toma snapshots mientras los hilos siguen escribiendo: no debe lanzar.
            while (done.getCount() > 0) {
                stats.snapshot();
            }
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        GenerationStats.Snapshot snapshot = stats.snapshot();
        assertThat(snapshot.totalEvents()).isEqualTo((long) threads * perThread);
        assertThat(snapshot.perTopicStats()).hasSize(4);
        assertThat(snapshot.perTopicStats().values().stream().mapToLong(TopicStats::totalSent).sum())
                .isEqualTo((long) threads * perThread);
    }
}
