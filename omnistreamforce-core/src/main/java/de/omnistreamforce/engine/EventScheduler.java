package de.omnistreamforce.engine;

import de.omnistreamforce.util.RandomUtils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Controla la frecuencia de publicacion de eventos (events per second).
 * <p>
 * Modos soportados:
 * <ul>
 *   <li>STEADY: ritmo constante.</li>
 *   <li>BURST: rafagas periodicas de {@code burstSize} eventos.</li>
 *   <li>SPIKE: picos aleatorios que simulan cargas impredecibles.</li>
 *   <li>RAMP: incremento gradual hasta {@code rampTargetEPS}.</li>
 * </ul>
 * Usa un {@link ScheduledExecutorService} para controlar el timing con precision
 * de milisegundos y permite el ajuste dinamico de velocidad en caliente.
 */
public class EventScheduler {

    private static final int DEFAULT_TICK_MS = 100;
    private static final int BURST_PERIOD_TICKS = 10;

    private final int tickIntervalMs;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile PublishingMode mode;
    private volatile int targetEPS;
    private volatile int burstSize;
    private volatile int rampTargetEPS;
    private volatile int currentRampEPS;

    private double fractionalCarry = 0.0;
    private final AtomicLong tickCount = new AtomicLong();
    private ScheduledFuture<?> scheduledFuture;

    public EventScheduler(PublishingMode mode, int targetEPS) {
        this(mode, targetEPS, DEFAULT_TICK_MS, 0, 0);
    }

    public EventScheduler(PublishingMode mode, int targetEPS, int tickIntervalMs, int burstSize, int rampTargetEPS) {
        this.mode = mode == null ? PublishingMode.STEADY : mode;
        this.targetEPS = Math.max(0, targetEPS);
        this.tickIntervalMs = Math.max(1, tickIntervalMs);
        this.burstSize = burstSize;
        this.rampTargetEPS = rampTargetEPS;
        this.currentRampEPS = this.mode == PublishingMode.RAMP ? 0 : this.targetEPS;
        this.executor = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    }

    /**
     * Inicia el scheduler llamando a {@code tick} en cada intervalo.
     * Dentro de {@code tick} el consumidor debe consultar a {@link #tokensForTick()}
     * para saber cuantos eventos generar.
     */
    public synchronized void start(Runnable tick) {
        if (running.get()) {
            return;
        }
        running.set(true);
        tickCount.set(0);
        scheduledFuture = executor.scheduleAtFixedRate(() -> {
            try {
                tickCount.incrementAndGet();
                tick.run();
            } catch (RuntimeException e) {
                // evita que el scheduler se detenga por excepciones puntuales
            }
        }, 0, tickIntervalMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    public void shutdown() {
        stop();
        executor.shutdownNow();
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Ajuste dinamico de velocidad en tiempo de ejecucion.
     */
    public void setEventsPerSecond(int eps) {
        this.targetEPS = Math.max(0, eps);
        if (mode != PublishingMode.RAMP) {
            this.currentRampEPS = this.targetEPS;
        }
    }

    public void setMode(PublishingMode mode) {
        this.mode = mode == null ? PublishingMode.STEADY : mode;
        if (mode != PublishingMode.RAMP) {
            this.currentRampEPS = this.targetEPS;
        }
    }

    /**
     * Cuantos eventos deben generarse en el tick actual segun el modo configurado.
     */
    public synchronized int tokensForTick() {
        long tick = tickCount.get();
        return switch (mode) {
            case STEADY -> steadyTokens();
            case BURST -> burstTokens(tick);
            case SPIKE -> spikeTokens();
            case RAMP -> rampTokens();
        };
    }

    private int steadyTokens() {
        double goal = targetEPS * (tickIntervalMs / 1000.0);
        fractionalCarry += goal;
        int tokens = (int) fractionalCarry;
        fractionalCarry -= tokens;
        return tokens;
    }

    private int burstTokens(long tick) {
        if (tick % BURST_PERIOD_TICKS == 0) {
            return Math.max(burstSize, targetEPS);
        }
        return 0;
    }

    private int spikeTokens() {
        int base = steadyTokens();
        if (RandomUtils.chance(0.1)) {
            base *= RandomUtils.nextInt(3, 8);
        }
        return base;
    }

    private int rampTokens() {
        if (currentRampEPS < rampTargetEPS) {
            currentRampEPS = Math.min(rampTargetEPS, currentRampEPS + Math.max(1, rampTargetEPS / 20));
        }
        double goal = currentRampEPS * (tickIntervalMs / 1000.0);
        fractionalCarry += goal;
        int tokens = (int) fractionalCarry;
        fractionalCarry -= tokens;
        return tokens;
    }

    public int getTickIntervalMs() {
        return tickIntervalMs;
    }

    public PublishingMode getMode() {
        return mode;
    }

    public int getTargetEPS() {
        return targetEPS;
    }
}