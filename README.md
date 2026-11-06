# KB Dynamic Performance (Forge 1.20.1)

Mod **solo de servidor** que ajusta view distance, simulation distance y mob cap según el MSPT, con la misma lógica que Dynamic Performance.

## Compilar

`./gradlew build` → el jar queda en `build/libs/`.

Requiere JDK 17. Compilado contra Forge 47.4.2 (Minecraft 1.20.1), funciona con cualquier Forge 47.x.

## Cómo funciona

Cada `interval` segundos mide el MSPT medio de los últimos 100 ticks:

| MSPT | Acción |
|---|---|
| `>= lag_threshold` | Baja un paso el primer nivel (de arriba a abajo) que aún pueda bajar |
| `<= recovery_threshold` | Sube un paso el primer nivel (de abajo a arriba) que aún pueda subir |
| entre ambos | No toca nada |

Los cambios son en memoria: `server.properties` no se modifica.

## Config (`config/kb-dynamic-performance.json`)

```json
{
  "interval": 15,
  "lag_threshold": 45.0,
  "recovery_threshold": 30.0,
  "levels": [
    { "type": "SIMULATION_DISTANCE", "max": 10,  "min": 6,  "increment": 1 },
    { "type": "MOB_CAP_PERCENTAGE",  "max": 100, "min": 75, "increment": 5 },
    { "type": "SIMULATION_DISTANCE", "max": 6,   "min": 4,  "increment": 1 },
    { "type": "MOB_CAP_PERCENTAGE",  "max": 75,  "min": 40, "increment": 15 },
    { "type": "VIEW_DISTANCE",       "max": 10,  "min": 5,  "increment": 1 }
  ]
}
```

- Un mismo tipo puede aparecer en varios niveles con rangos encadenados (como la simulation distance arriba).
- Distancias: 2–32. Mob cap: 0–1000 %.
- `recovery_threshold` tiene que ser menor que `lag_threshold`.

## Comandos (nivel OP 2)

- `/dp status` — estado, MSPT, TPS y valores actuales
- `/dp config` — config activa
- `/dp reload` — recarga sin reiniciar; si el JSON es inválido mantiene el anterior

`/dynamicperformance` funciona como alias.
