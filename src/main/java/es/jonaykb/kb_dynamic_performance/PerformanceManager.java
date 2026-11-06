package es.jonaykb.kb_dynamic_performance;

import es.jonaykb.kb_dynamic_performance.DynPerfConfig.Level;
import es.jonaykb.kb_dynamic_performance.DynPerfConfig.LevelType;
import net.minecraft.server.MinecraftServer;

import java.util.EnumMap;
import java.util.Map;

/**
 * Lógica de escalado (todo se ejecuta en el hilo del servidor):
 *  - MSPT >= lag_threshold      -> baja un paso del primer nivel (de arriba a abajo) que aún pueda bajar
 *  - MSPT <= recovery_threshold -> sube un paso del primer nivel (de abajo a arriba) que aún pueda subir
 *  - entre ambos                -> no toca nada (histéresis)
 */
public final class PerformanceManager {

    public enum State { OPTIMAL, STABLE, SCALING_DOWN, LAGGING, SCALING_UP }

    private static DynPerfConfig config;
    private static MinecraftServer server;

    private static final EnumMap<LevelType, Integer> current = new EnumMap<>(LevelType.class);
    private static int originalView = 10;
    private static int originalSim = 10;

    private static long lastCheckNanos;
    private static double lastMspt;
    private static State state = State.OPTIMAL;

    /** Leído desde los mixins de spawn (mismo hilo del servidor). */
    private static volatile int mobCapPercent = 100;

    private PerformanceManager() {}

    // ---------------------------------------------------------------- ciclo de vida

    public static void init(DynPerfConfig cfg, MinecraftServer srv) {
        server = srv;
        originalView = srv.getPlayerList().getViewDistance();
        originalSim = srv.getPlayerList().getSimulationDistance();

        config = cfg;
        current.clear();
        for (Level l : cfg.levels) {
            current.merge(l.type, l.max, Math::max);
        }
        current.forEach(PerformanceManager::apply);

        state = State.OPTIMAL;
        lastMspt = 0;
        lastCheckNanos = System.nanoTime();

        KbDynPerf.LOGGER.info("[KB DynPerf] Iniciado. Valores iniciales: {} (server.properties: view={}, sim={})",
                current, originalView, originalSim);
    }

    /** Aplica una config nueva sin reiniciar, ajustando los valores actuales a los rangos nuevos. */
    public static void reload(DynPerfConfig cfg) {
        if (server == null) return;

        EnumMap<LevelType, Integer> newMin = new EnumMap<>(LevelType.class);
        EnumMap<LevelType, Integer> newMax = new EnumMap<>(LevelType.class);
        for (Level l : cfg.levels) {
            newMin.merge(l.type, l.min, Math::min);
            newMax.merge(l.type, l.max, Math::max);
        }

        // Tipos que ya no están en la config -> volver a su valor original
        for (LevelType type : LevelType.values()) {
            if (current.containsKey(type) && !newMax.containsKey(type)) {
                current.remove(type);
                apply(type, originalValue(type));
            }
        }

        // Tipos nuevos empiezan en su máximo; los existentes se recortan al nuevo rango
        for (Map.Entry<LevelType, Integer> e : newMax.entrySet()) {
            LevelType type = e.getKey();
            int value = current.getOrDefault(type, e.getValue());
            value = Math.max(newMin.get(type), Math.min(e.getValue(), value));
            current.put(type, value);
            apply(type, value);
        }

        config = cfg;
        lastCheckNanos = System.nanoTime();
        KbDynPerf.LOGGER.info("[KB DynPerf] Config recargada. Valores actuales: {}", current);
    }

    public static void shutdown() {
        server = null;
        config = null;
        current.clear();
        mobCapPercent = 100;
        state = State.OPTIMAL;
    }

    // ---------------------------------------------------------------- tick

    public static void onTick() {
        if (server == null || config == null) return;

        long now = System.nanoTime();
        if (now - lastCheckNanos < config.interval * 1_000_000_000L) return;
        lastCheckNanos = now;

        evaluate();
    }

    private static void evaluate() {
        double mspt = averageMspt();
        lastMspt = mspt;

        if (mspt >= config.lagThreshold) {
            Level level = findLevelToScaleDown();
            if (level != null) {
                step(level, -level.increment);
                state = State.SCALING_DOWN;
            } else {
                state = State.LAGGING;
            }
        } else if (mspt <= config.recoveryThreshold) {
            Level level = findLevelToScaleUp();
            if (level != null) {
                step(level, level.increment);
                state = State.SCALING_UP;
            } else {
                state = State.OPTIMAL;
            }
        } else {
            state = isFullyRestored() ? State.OPTIMAL : State.STABLE;
        }
    }

    /** Primer nivel (de arriba a abajo) cuyo tipo esté dentro de su rango y por encima de su mínimo. */
    private static Level findLevelToScaleDown() {
        for (Level l : config.levels) {
            int cur = current.get(l.type);
            if (cur > l.min && cur <= l.max) return l;
        }
        return null;
    }

    /** Primer nivel (de abajo a arriba) cuyo tipo esté dentro de su rango y por debajo de su máximo. */
    private static Level findLevelToScaleUp() {
        for (int i = config.levels.size() - 1; i >= 0; i--) {
            Level l = config.levels.get(i);
            int cur = current.get(l.type);
            if (cur >= l.min && cur < l.max) return l;
        }
        return null;
    }

    private static boolean isFullyRestored() {
        return findLevelToScaleUp() == null;
    }

    private static void step(Level level, int delta) {
        int before = current.get(level.type);
        int after = Math.max(level.min, Math.min(level.max, before + delta));
        if (after == before) return;

        current.put(level.type, after);
        apply(level.type, after);

        KbDynPerf.LOGGER.info("[KB DynPerf] {} {} -> {} (MSPT {})",
                level.type, before, after, String.format("%.1f", lastMspt));
    }

    private static void apply(LevelType type, int value) {
        switch (type) {
            case VIEW_DISTANCE -> server.getPlayerList().setViewDistance(value);
            case SIMULATION_DISTANCE -> server.getPlayerList().setSimulationDistance(value);
            case MOB_CAP_PERCENTAGE -> mobCapPercent = value;
        }
    }

    private static int originalValue(LevelType type) {
        return switch (type) {
            case VIEW_DISTANCE -> originalView;
            case SIMULATION_DISTANCE -> originalSim;
            case MOB_CAP_PERCENTAGE -> 100;
        };
    }

    /** Media de los últimos 100 ticks, en milisegundos. */
    public static double averageMspt() {
        if (server == null) return 0;
        long[] times = server.tickTimes;
        long sum = 0;
        int count = 0;
        for (long t : times) {
            if (t > 0) {
                sum += t;
                count++;
            }
        }
        return count == 0 ? 0 : (sum / (double) count) / 1_000_000.0;
    }

    // ---------------------------------------------------------------- usado por mixins / comandos

    /** Escala el máximo de mobs de una categoría según el porcentaje actual. */
    public static int scaleMobCap(int vanillaMax) {
        int pct = mobCapPercent;
        if (pct == 100) return vanillaMax;
        return Math.max(0, Math.round(vanillaMax * pct / 100f));
    }

    public static boolean isRunning() { return server != null && config != null; }
    public static DynPerfConfig config() { return config; }
    public static State state() { return state; }
    public static double lastMspt() { return lastMspt; }
    public static int mobCapPercent() { return mobCapPercent; }

    public static int viewDistance() {
        return server == null ? 0 : server.getPlayerList().getViewDistance();
    }

    public static int simulationDistance() {
        return server == null ? 0 : server.getPlayerList().getSimulationDistance();
    }

    public static long secondsToNextCheck() {
        if (config == null) return 0;
        long elapsed = (System.nanoTime() - lastCheckNanos) / 1_000_000_000L;
        return Math.max(0, config.interval - elapsed);
    }
}
