package es.jonaykb.kb_dynamic_performance;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Config compatible en formato con Dynamic Performance:
 * {
 *   "interval": 15,
 *   "lag_threshold": 45.0,
 *   "recovery_threshold": 30.0,
 *   "levels": [ { "type": "VIEW_DISTANCE", "max": 10, "min": 5, "increment": 1 }, ... ]
 * }
 */
public final class DynPerfConfig {

    public enum LevelType { VIEW_DISTANCE, SIMULATION_DISTANCE, MOB_CAP_PERCENTAGE }

    public static final class Level {
        public LevelType type;
        public int max;
        public int min;
        public int increment = 1;

        public Level() {}

        public Level(LevelType type, int max, int min, int increment) {
            this.type = type;
            this.max = max;
            this.min = min;
            this.increment = increment;
        }

        @Override
        public String toString() {
            return type + " " + min + "–" + max + " (±" + increment + ")";
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    /** Segundos entre comprobaciones. */
    public int interval = 15;
    /** MSPT a partir del cual se empieza a bajar. */
    public double lagThreshold = 45.0;
    /** MSPT igual o por debajo del cual se empieza a recuperar. */
    public double recoveryThreshold = 30.0;
    /** Orden de bajada: de arriba a abajo. Subida: de abajo a arriba. */
    public List<Level> levels = new ArrayList<>();

    public static DynPerfConfig defaults() {
        DynPerfConfig c = new DynPerfConfig();
        // Lo que más pesa en el TPS primero; la view distance (lo más visible) al final
        c.levels.add(new Level(LevelType.SIMULATION_DISTANCE, 10, 6, 1));
        c.levels.add(new Level(LevelType.MOB_CAP_PERCENTAGE, 100, 75, 5));
        c.levels.add(new Level(LevelType.SIMULATION_DISTANCE, 6, 4, 1));
        c.levels.add(new Level(LevelType.MOB_CAP_PERCENTAGE, 75, 40, 15));
        c.levels.add(new Level(LevelType.VIEW_DISTANCE, 10, 5, 1));
        return c;
    }

    public static DynPerfConfig loadOrCreate(Path path) throws IOException {
        if (Files.notExists(path)) {
            DynPerfConfig def = defaults();
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(def, w);
            }
            KbDynPerf.LOGGER.info("[KB DynPerf] Creada config por defecto en {}", path);
            return def;
        }
        return load(path);
    }

    public static DynPerfConfig load(Path path) throws IOException {
        DynPerfConfig c;
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            c = GSON.fromJson(r, DynPerfConfig.class);
        } catch (RuntimeException e) {
            throw new IOException("JSON mal formado: " + e.getMessage(), e);
        }
        if (c == null) throw new IOException("El archivo está vacío");
        c.validate();
        return c;
    }

    private void validate() throws IOException {
        if (interval < 1) throw new IOException("interval debe ser >= 1");
        if (recoveryThreshold >= lagThreshold)
            throw new IOException("recovery_threshold debe ser menor que lag_threshold");
        if (levels == null) levels = new ArrayList<>();

        for (int i = 0; i < levels.size(); i++) {
            Level l = levels.get(i);
            String where = "levels[" + i + "]";
            if (l == null || l.type == null) throw new IOException(where + ": falta 'type' o no es válido");
            if (l.min > l.max) throw new IOException(where + ": min no puede ser mayor que max");
            if (l.increment < 1) throw new IOException(where + ": increment debe ser >= 1");

            switch (l.type) {
                case VIEW_DISTANCE, SIMULATION_DISTANCE -> {
                    if (l.min < 2 || l.max > 32)
                        throw new IOException(where + ": las distancias deben estar entre 2 y 32");
                }
                case MOB_CAP_PERCENTAGE -> {
                    if (l.min < 0 || l.max > 1000)
                        throw new IOException(where + ": el porcentaje debe estar entre 0 y 1000");
                }
            }
        }
    }
}
