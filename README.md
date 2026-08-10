# OmniStreamForce

Generador de datos configurable para **Apache Kafka**: eliges uno o varios dominios de negocio,
los mapeas a topics y empieza a publicar eventos realistas —normales y de error— al ritmo que
quieras. Además de publicar directo a Kafka, puede escribir los eventos en una base de datos con
el **patrón Outbox** y sacarlos hacia Kafka por un **relay propio** o por **CDC con Debezium**.

Java 21 · Maven multi-módulo · sin frameworks pesados.

---

## Qué hace

| | |
|---|---|
| **Multi-dominio** | Dominios como plugins descubiertos por SPI: healthcare, ecommerce y fastfood (franquicias Burger King y McDonald's) |
| **Multi-topic** | Cada dominio publica a su topic, con topic separado para los errores si se quiere |
| **Dual-stream** | Eventos normales y de error mezclados, con tasa de error configurable y errores coherentes (no ruido aleatorio) |
| **Control de flujo** | Modos STEADY, BURST, SPIKE y RAMP; EPS y tasa de error ajustables en caliente |
| **Multi-formato** | JSON, Avro y Protobuf |
| **Cloud-native** | Local, AWS MSK (IAM y SASL/SCRAM) y Confluent Cloud |
| **Persistencia** | Patrón Outbox real: fila de negocio y fila de outbox en la misma transacción |
| **Dos salidas del outbox** | Relay propio por polling, o Debezium leyendo el WAL. O ambos, para compararlos |

## Arranque rápido

Requisitos: **JDK 21** y **Maven 3.9+**. Para los tests de integración y el stack de CDC, Docker.

```bash
mvn clean package -DskipTests
java -jar omnistreamforce-cli/target/omnistreamforce-cli.jar list-domains
```

Con un Kafka en `localhost:9092`:

```bash
java -jar omnistreamforce-cli/target/omnistreamforce-cli.jar connect -b localhost:9092
java -jar omnistreamforce-cli/target/omnistreamforce-cli.jar interactive
```

El flujo interactivo pregunta por: cluster → topics → dominios y su mapeo → destino → formato,
modo, duración y estrategia de clave. Después muestra un panel en vivo por dominio y por topic.

```
Paso 3: Dominios y mapeo a topics
  Dominio: fastfood
    Eventos normales: OrderPlaced, OrderPaid, KitchenPrepStarted, ...
    Eventos de error: PaymentDeclined, KitchenDelay, ColdChainBreach, ...
? Topic de destino [fastfood-events]:
? Publicar los errores en un topic aparte? (S/n):
? Eventos por segundo [50]: 120
? Tasa de error (%) [10.0]: 15
```

## Comandos

| Comando | Para qué |
|---|---|
| `interactive` | Flujo guiado completo y dashboard en vivo |
| `batch --profile <p>` / `--config <f>` | Ejecución sin interacción, para CI o dejarlo corriendo |
| `list-profiles [<p>] [--yaml]` | Perfiles disponibles y su detalle |
| `connect -b <servers>` | Valida el acceso a un cluster y lista sus topics |
| `list-domains [-d]` | Dominios disponibles; con `-d`, también sus campos |
| `propose-schema <dominio>` | Propone con IA el esquema de un dominio nuevo |
| `web [-p 8080]` | Interfaz web |
| `help` | Ayuda |

En el panel de publicación: `P` pausa, `R` reanuda, `+`/`-` ajustan el ritmo, `E <n>` cambia la
tasa de error y `S` para. Cada comando se confirma con Enter.

## Configuración y perfiles

Todo lo que pregunta el flujo interactivo cabe en un YAML, y hay siete perfiles listos:

```bash
java -jar omnistreamforce-cli/target/omnistreamforce-cli.jar list-profiles
java -jar ...jar batch --profile multi-domain --dry-run --duration 10
java -jar ...jar batch --profile outbox-dual --duration 60 --eps 200
java -jar ...jar batch --config mi-config.yaml
```

`--dry-run` genera con los generadores reales y estima el volumen **sin abrir ninguna conexión**.
`--duration` y `--eps` sobrescriben lo que traiga el perfil.

```yaml
kafka:
  bootstrapServers: ${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
domains:
  - domain: fastfood
    topic: fastfood-events
    errorTopic: fastfood-errors
    eventsPerSecond: 120
    errorRate: 15
    keyStrategy: ENTITY_ID
    keyField: orderId
generation:
  publishingMode: STEADY      # STEADY | BURST | SPIKE | RAMP
  durationSeconds: 0          # 0 = sin límite
sink:
  type: DUAL                  # KAFKA | DB_OUTBOX | DUAL
  jdbcUrl: ${OSF_DB_URL:-jdbc:postgresql://localhost:55433/osf}
  password: ${OSF_DB_PASSWORD}
```

Los valores admiten `${VAR}`, `${env:VAR}` y `${VAR:-por-defecto}`, así que las credenciales no
se escriben en el fichero. Los perfiles propios se buscan en `~/.omnistreamforce/profiles/` y en
`./config/profiles/`, y ganan al perfil de fábrica del mismo nombre.

## Dominios

Los dominios se descubren en el classpath por `ServiceLoader`, así que añadir uno **no requiere
tocar el core**: basta un módulo nuevo que implemente `DomainGenerator` y se registre en
`META-INF/services`.

| Dominio | Eventos normales | Eventos de error |
|---|---|---|
| `healthcare` | 5 (admisión, constantes, medicación, laboratorio, alta) | 4 |
| `ecommerce` | 5 (pedido, pago, inventario, envío, cancelación) | 4 |
| `fastfood` | 13 (pedidos y cocina, drive-thru y canales, inventario) | 8 |
| `energy` | 4 (generación, estado de red, contador, precio) | 3 |
| `autos` | 4 (telemetría, estado, viaje, mantenimiento) | 4 |
| `highway` | 4 (tráfico, paso de vehículo, incidente, peaje) | 3 |

`fastfood` modela franquicias: la marca viaja en el payload (`brand`) y en los identificadores
(`storeId=BK-4821`, `orderId=ORD-MCD-38472910`), con carta e ingredientes propios de cada una.
Añadir otra franquicia es añadir una constante al enum `FastFoodBrand`.

Los eventos de error son coherentes con su semántica, no valores al azar: `KitchenDelay` supera
siempre el SLA de cocina, `ColdChainBreach` supera el umbral de −18 °C, `EngineOverheat` pasa de
110 °C, `TrafficJam` baja de 20 km/h y `GridFrequencyDeviation` se sale del margen de ±0,2 Hz.
Los datos normales también guardan relación: las rpm acompañan a la velocidad, una central nuclear
no rinde como una solar, y a más densidad de tráfico menos velocidad media.

## Destinos

```
DomainGenerator ─▶ GenerationEngine ─▶ EventPublisher
                                          │
              ┌───────────────────────────┼───────────────────────────┐
              ▼                           ▼                           ▼
      KafkaEventPublisher        JdbcOutboxPublisher          CompositePublisher
              │                           │                      (los dos)
            Kafka          tabla de negocio + osf_outbox
                                          │
                        ┌─────────────────┴─────────────────┐
                        ▼                                   ▼
                 OutboxRelay (polling)              Debezium (CDC del WAL)
                 → fastfood-events                  → cdc.fastfood-events
```

`PublisherFactory.create(SinkConfig)` devuelve el publisher según el destino elegido —`KAFKA`,
`DB_OUTBOX` o `DUAL`— **sin cambiar la firma del motor**:

```java
EventPublisher publisher = PublisherFactory.create(sinkConfig);
MultiDomainEngine engine = new MultiDomainEngine(serializer, publisher);
```

### Patrón Outbox

Cada evento se escribe como fila de negocio **y** fila de outbox en la misma transacción, así que
nunca existe una sin la otra. Las tablas de negocio se generan a partir del `EventSchema` de cada
dominio: una columna tipada por campo declarado, más `payload` (el evento completo), `metadata` y
`payload_extra` con las claves que el esquema no declara. Esa última columna convierte la deriva
de esquema en algo medible:

```sql
SELECT k, count(*) FROM osf_fastfood_events, LATERAL jsonb_object_keys(payload_extra) k
GROUP BY 1 ORDER BY 2 DESC;
```

`publish()` no toca la base de datos: encola, y unos hilos escritores agrupan en lotes (una
transacción por lote). El motor trabaja con ticks de 100 ms por dominio, y un INSERT síncrono por
evento se comería ese presupuesto.

### Relay y CDC

El stack de `docker/` levanta PostgreSQL con replicación lógica, Kafka y Debezium:

```bash
docker compose -f docker/docker-compose.yml up -d
./docker/register-connector.sh        # register-connector.ps1 en Windows
```

Detalles y verificación paso a paso en [`docker/README.md`](docker/README.md).

El relay y el CDC pueden convivir publicando a topics distintos (`fastfood-events` y
`cdc.fastfood-events`), lo que permite compararlos sin duplicar nada.

## Dominios propuestos por IA

Para un dominio que no existe todavía, un modelo puede proponer el esquema y el generador lo
convierte en eventos publicables:

```bash
# con Ollama en local (sin credenciales)
java -jar ...jar propose-schema logistics -d "paquetería urbana con entregas en el día"

# con OpenAI
OSF_AI_API_KEY=sk-... java -jar ...jar propose-schema logistics --provider openai --model gpt-4o-mini
```

`SchemaBackedGenerator` convierte **cualquier** `EventSchema` en un `DomainGenerator` usable por
el motor, así que el dominio propuesto se publica igual que los escritos a mano. Funciona también
con esquemas propios, sin IA de por medio.

La IA es siempre opcional: si no está configurada o no responde, el comando lo dice y el resto del
generador sigue funcionando igual.

## Módulos

```
omnistreamforce-core          modelo, motor, routing, serializadores y capa Kafka
omnistreamforce-domains       un submódulo por dominio (healthcare, ecommerce, fastfood, ...)
omnistreamforce-persistence   sink de base de datos, generación de DDL, outbox y relay
omnistreamforce-cli           interfaz de línea de comandos
omnistreamforce-web           interfaz web
omnistreamforce-ai            propuesta de esquemas con LLM (OpenAI y Ollama)
```

## Tests

```bash
mvn clean test        # unitarios; no necesitan Docker
mvn verify -Pit       # integración: Testcontainers, o infraestructura ya levantada
```

Los tests de integración se llaman `*IT.java` y los ejecuta failsafe, de modo que `mvn test`
nunca depende de Docker. Si Testcontainers no puede hablar con el demonio local, se les puede
indicar una infraestructura existente:

```bash
OSF_IT_JDBC_URL=jdbc:postgresql://localhost:5432/osf \
OSF_IT_KAFKA_BOOTSTRAP=localhost:9092 mvn verify -Pit
```

## Estado

| Fase | Estado |
|---|---|
| 1–5 Fundación, dominios, motor, Kafka, serializadores | Completado |
| 6b Dominio de franquicias (Burger King, McDonald's) | Completado |
| 8 CLI interactivo | Completado |
| 11–13 Persistencia con patrón Outbox | Completado |
| 14 Relay del outbox | Completado |
| 15 CDC con Debezium | Completado |
| 16 Selección de destino (Kafka / Outbox / Dual) | Completado |
| 9 Configuración YAML, perfiles y modo batch | Completado |
| 6 Dominios energy, autos y highway | Completado |
| 7 Integración con IA (OpenAI y Ollama) | Completado |
| 10 Distribución y empaquetado | Pendiente |

El plan completo, con el detalle de cada fase y sus criterios de aceptación, está en
[`docs/OmniStreamForce-PLAN.md`](docs/OmniStreamForce-PLAN.md).