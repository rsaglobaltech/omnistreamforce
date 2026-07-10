package de.omnistreamforce.engine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EventSchedulerTest {

    @Test
    void steadyProducesApproximatelyTargetEps() throws InterruptedException {
        EventScheduler scheduler = new EventScheduler(PublishingMode.STEADY, 100, 100, 0, 0);
        AtomicInteger produced = new AtomicInteger();
        scheduler.start(() -> produced.addAndGet(scheduler.tokensForTick()));
        Thread.sleep(1000);
        scheduler.shutdown();
        // 100 EPS con tick de 100ms => ~10 tokens por tick, ~100 en 1s
        assertThat(produced.get()).isBetween(70, 140);
    }

    @Test
    void rampIncreasesTokensOverTime() throws InterruptedException {
        EventScheduler scheduler = new EventScheduler(PublishingMode.RAMP, 0, 100, 0, 200);
        int firstWindow = 0;
        int secondWindow = 0;
        long end = System.currentTimeMillis() + 1100;
        boolean firstDone = false;
        while (System.currentTimeMillis() < end) {
            int tokens = scheduler.tokensForTick();
            if (System.currentTimeMillis() < end - 600) {
                firstWindow += tokens;
            } else if (System.currentTimeMillis() < end - 300) {
                // ventana intermedia
            } else {
                if (!firstDone) {
                    firstDone = true;
                }
                secondWindow += tokens;
            }
            Thread.sleep(100);
        }
        scheduler.shutdown();
        assertThat(secondWindow).isGreaterThanOrEqualTo(firstWindow);
    }

    @Test
    void dynamicEpsChangeTakesEffect() throws InterruptedException {
        EventScheduler scheduler = new EventScheduler(PublishingMode.STEADY, 0, 100, 0, 0);
        AtomicInteger produced = new AtomicInteger();
        scheduler.start(() -> produced.addAndGet(scheduler.tokensForTick()));
        Thread.sleep(300);
        int before = produced.get();
        scheduler.setEventsPerSecond(100);
        Thread.sleep(700);
        int after = produced.get();
        scheduler.shutdown();
        assertThat(after - before).isGreaterThan(before);
    }
}