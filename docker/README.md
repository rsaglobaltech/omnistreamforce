# Stack de CDC (PostgreSQL + Kafka + Debezium)

Levanta lo mínimo para ver el patrón Outbox de punta a punta: el generador escribe en
PostgreSQL, y los eventos salen hacia Kafka por **dos caminos comparables**:

| Camino | Quién lo mueve | Topic |
|---|---|---|
| Relay propio (polling) | `OutboxRelay` dentro de la aplicación | `fastfood-events` |
| CDC | Debezium leyendo el WAL | `cdc.fastfood-events` |

Como salen a topics distintos, se pueden comparar latencia y orden lado a lado sin duplicar nada.

## Arrancar

```bash
docker compose -f docker/docker-compose.yml up -d
./docker/register-connector.sh          # Linux / macOS
./docker/register-connector.ps1         # Windows
```

Con interfaz web de Kafka (opcional, en http://localhost:8080):

```bash
docker compose -f docker/docker-compose.yml --profile tools up -d
```

| Servicio | Puerto local | Notas |
|---|---|---|
| PostgreSQL | `55433` | usuario/clave/base: `osf`. Desplazado para no chocar con un Postgres local |
| Kafka | `19092` | listener EXTERNAL; dentro de la red los contenedores usan `osf-cdc-kafka:9092` |
| Kafka Connect | `8083` | API REST de Debezium |
| Kafka UI | `8080` | solo con `--profile tools` |

## Puntos del montaje que no son obvios

- **`wal_level=logical`**: sin esto Debezium no puede leer el WAL y el conector ni arranca.
  Va en el `command` del servicio de PostgreSQL.
- **`skipped.operations: "u,d,t"`**: el relay hace `UPDATE` sobre las mismas filas que Debezium
  vigila. Sin esta opción, cada `UPDATE` se emitiría como un evento más y habría duplicados en
  el topic `cdc.*`. Solo se rutean las inserciones. Como efecto secundario, tampoco hace falta
  tocar `REPLICA IDENTITY`.
- **`route.by.field: aggregatetype`**: en este diseño `aggregatetype` **es** el topic de destino
  que ya resolvió el `TopicRouter`, así que `route.topic.replacement: "cdc.${routedByValue}"`
  reproduce el mismo nombre con prefijo. Quitando el prefijo saldrían los topics idénticos a los
  del relay (útil si se quiere CDC en vez de relay, no los dos).
- **`table.expand.json.payload: true`** + `schemas.enable=false`: lo que llega al topic es el
  `Event` limpio, no el envelope `before/after/op/source` de Debezium.
- **`table.fields.additional.placement`**: el `EventRouter` descarta todo lo que no sean sus cinco
  columnas canónicas, así que `domain`, `trace_id` y `created_at` se conservan como cabeceras.
- **Registro con `PUT /config`** en vez de `POST /connectors`: es idempotente y no falla si el
  conector ya existe.

## Verificación end-to-end

```bash
# 1. El conector está vivo
curl -s localhost:8083/connectors/osf-outbox-connector/status

# 2. Generar eventos con el sink de base de datos (SinkType.DB_OUTBOX o DUAL)
#    apuntando a jdbc:postgresql://localhost:55433/osf

# 3. Las dos tablas tienen lo mismo
docker exec osf-cdc-postgres psql -U osf -d osf -c \
  "SELECT (SELECT count(*) FROM osf_outbox) AS outbox, (SELECT count(*) FROM osf_fastfood_events) AS negocio;"

# 4. El evento llega desenvuelto y con cabeceras
docker exec osf-cdc-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic cdc.fastfood-events \
  --from-beginning --property print.key=true --property print.headers=true --max-messages 1

# 5. El slot de replicación no acumula WAL sin control
docker exec osf-cdc-postgres psql -U osf -d osf -c \
  "SELECT slot_name, active, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) AS retenido FROM pg_replication_slots;"
```

Resultado esperado en el paso 4: la clave es el `aggregateid` (por ejemplo `ORD-BK-14978795`),
el valor es el `Event` completo, y las cabeceras traen `id`, `eventType`, `domain`, `traceId`
y `createdAt`.

## Parar y limpiar

```bash
docker compose -f docker/docker-compose.yml down          # conserva los datos
docker compose -f docker/docker-compose.yml down -v       # borra tambien el volumen
```

> **Cuidado con el slot de replicación.** Si se borra el conector sin borrar el slot, PostgreSQL
> retiene WAL indefinidamente hasta llenar el disco. Al eliminar el conector para siempre:
>
> ```sql
> SELECT pg_drop_replication_slot('osf_outbox_slot');
> ```
>
> `down -v` elimina el volumen entero, así que el slot desaparece con él.
