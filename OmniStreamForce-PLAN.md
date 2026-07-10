# OmniStreamForce

> Generador de datos configurable y adaptable para Apache Kafka con soporte multi-dominio, multi-topic y multi-plataforma potenciado por IA.

---

## Vision General

**OmniStreamForce** es una herramienta CLI escrita en Java que actua como un productor de datos generico para Apache Kafka. Permite a los usuarios conectarse a cualquier cluster de Kafka (local, self-hosted, o gestionado en la nube como AWS MSK), seleccionar uno o multiples dominios de negocio (healthcare, ecommerce, energy, autos, highway, etc.), elegir interactivamente los topics de destino para cada dominio, propone automaticamente una estructura de eventos usando IA, y comienza a publicar eventos (normales y de error) a multiples topics simultaneamente con frecuencia y velocidad configurables. El CLI es instalable en Windows, Linux y macOS.

### Objetivos Clave

- **Generico y Adaptable**: Arquitectura extensible por dominio mediante plugins/estrategias.
- **Multidominio**: Soporte nativo para multiples dominios de negocio.
- **Eventos de Error y Normales**: Generacion automatica de eventos validos y eventos de error/falla.
- **Potenciado por IA**: Propuesta de esquemas de eventos y generacion de datos realistas mediante LLM.
- **CLI Interactivo**: Flujo guiado de configuracion desde la terminal.
- **Configurable**: Frecuencia, velocidad, formato, volumen y tasa de errores ajustables.
- **Control de Flujo**: Configuracion granular de la frecuencia (ej. 100 mensajes por segundo) y duracion, con modos de publicacion steady, burst, spike y ramp.
- **Dual-Stream**: Generacion simultanea de eventos normales y eventos de error (campos faltantes, valores fuera de rango, formatos invalidos) en un mismo flujo de publicacion, con tasa de error configurable.
- **Multi-Topic Routing**: Cada dominio puede publicar a un topic diferente (ej: dominio healthcare -> topic `healthcare-events`, dominio ecommerce -> topic `ecommerce-events`), con seleccion interactiva de topics desde el cluster conectado.
- **Conectividad Cloud-Native**: Soporte para clusters locales, self-hosted y gestionados en la nube (AWS MSK, Confluent Cloud, Azure Event Hubs) con autenticacion SASL/SSL y IAM.
- **Cross-Platform**: Instalable y ejecutable en Windows, Linux y macOS mediante fat JAR, binarios nativos (GraalVM) y scripts de instalacion por plataforma.

---

## Arquitectura Propuesta

```
┌──────────────────────────────────────────────────────────────┐
│                       OmniStreamForce CLI                       │
│  (Picocli - flujo interactivo y modo batch)                   │
├──────────────┬───────────────┬───────────────────────────────┤
│  Config      │  AI Service   │  Domain Registry              │
│  Manager     │  (LLM: schema │  (descubrimiento de           │
│  (YAML/JSON) │   proposal)   │   dominios disponibles)       │
├──────────────┴───────────────┴───────────────────────────────┤
│                     Data Generation Engine                     │
│  ┌────────────┐ ┌────────────┐ ┌────────────────────────────┐│
│  │ Healthcare │ │ Ecommerce  │ │ Energy / Autos / Highway  ││
│  │ Generator  │ │ Generator  │ │ Generators                 ││
│  └─────┬──────┘ └─────┬──────┘ └────────────┬───────────────┘│
│        │               │                     │                │
│        └───────────────┴─────────────────────┘                │
│                        │                                       │
│              ┌─────────▼──────────┐                           │
│              │  Event Transformer │  (JSON/Avro/Protobuf)      │
│              └─────────┬──────────┘                           │
│                        │                                       │
│              ┌─────────▼──────────┐                           │
│              │   Topic Router      │  mapea dominio -> topic   │
│              │   (routing de       │  soporta multi-topic      │
│              │    eventos por       │  simultaneo              │
│              │    dominio)          │                          │
│              └─────────┬──────────┘                           │
├────────────────────────┼──────────────────────────────────────┤
│              ┌─────────▼──────────┐                           │
│              │  Kafka Producer     │                           │
│              │  (conexion, retry,  │  ┌─────────────────────┐ │
│              │   callbacks,        │  │ Cluster Backends:    │ │
│              │   multi-topic)      │  │ - Local / Self-host  │ │
│              └────────────────────┘  │ - AWS MSK (SASL/SSL) │ │
│                                      │ - Confluent Cloud    │ │
│                                      └─────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### Decisiones Tecnologicas

| Componente        | Tecnologia                  | Justificacion                                  |
|-------------------|-----------------------------|------------------------------------------------|
| Lenguaje          | Java 21 (LTS)               | Maduro, ecosistema Kafka nativo                |
| Build             | Maven o Gradle             | Estandar en proyectos Java enterprise          |
| CLI Framework     | Picocli                     | Potente, soporta modos interactivo y batch     |
| Kafka Client      | Apache Kafka Producer API  | Oficial, configuracion flexible               |
| Cloud Kafka       | AWS MSK SDK (SASL/SSL/IAM) | Soporte nativo para clusters gestionados MSK  |
| Serializacion     | Jackson (JSON) + Avro/Proto | Flexibilidad de formato                        |
| IA / LLM          | OpenAI API / Ollama local  | Propuesta de esquemas y generacion de datos   |
| Data Faker        | JavaFaker / DataFaker       | Datos realistas sin IA                         |
| Config           | YAML (SnakeYAML)           | Legible para perfiles de configuracion         |
| Logging           | SLF4J + Logback            | Estandar Java, niveles configurables           |
| Testing           | JUnit 5 + Testcontainers   | Tests de integracion con Kafka real            |
| Empaquetado       | Fat JAR / GraalVM Native   | Distribucion sencilla o nativa                 |
| Distribucion OS   | JReleaser / SDKMAN! / Scoop | Instalacion en Windows (Scoop), Linux (SDKMAN!), macOS (Homebrew) |

---

## Estructura del Proyecto

```
omnistreamforce/
├── pom.xml (o build.gradle)
├── omnistreamforce-core/                  # nucleo: interfaces, motor, kafka producer
│   ├── src/main/java/de/omnistreamforce/
│   │   ├── core/
│   │   │   ├── Event.java                    # modelo base de evento
│   │   │   ├── EventSchema.java              # definicion de esquema de un dominio
│   │   │   ├── EventType.java                # NORMAL, ERROR, WARNING, INFO
│   │   │   └── Severity.java
│   │   ├── domain/
│   │   │   ├── DomainGenerator.java          # interfaz/estrategia
│   │   │   ├── DomainRegistry.java          # registro y descubrimiento
│   │   │   └── DomainContext.java            # contexto de configuracion del dominio
│   │   ├── engine/
│   │   │   ├── GenerationEngine.java         # orquestador del ciclo de generacion
│   │   │   ├── EventScheduler.java          # control de frecuencia/velocidad
│   │   │   └── ErrorInjector.java           # inyeccion de errores controlada
│   │   ├── routing/
│   │   │   ├── TopicRouter.java             # mapea dominio -> topic, multi-topic routing
│   │   │   ├── TopicMapping.java            # modelo de mapeo dominio/topic
│   │   │   └── DomainTopicConfig.java       # configuracion de routing por dominio
│   │   ├── kafka/
│   │   │   ├── KafkaConnectionManager.java  # conexion y configuracion del producer
│   │   │   ├── KafkaEventPublisher.java     # publicacion con callbacks
│   │   │   ├── KafkaTopicManager.java       # creacion/verificacion/listado de topics
│   │   │   └── MskClusterConfig.java       # configuracion especifica de AWS MSK
│   │   ├── serializer/
│   │   │   ├── EventSerializer.java         # interfaz
│   │   │   ├── JsonEventSerializer.java
│   │   │   ├── AvroEventSerializer.java
│   │   │   └── ProtobufEventSerializer.java
│   │   ├── ai/
│   │   │   ├── AIService.java               # interfaz para LLM
│   │   │   ├── OpenAIService.java           # implementacion con OpenAI
│   │   │   ├── OllamaService.java           # implementacion local con Ollama
│   │   │   └── SchemaProposer.java          # propone esquemas para dominios
│   │   ├── config/
│   │   │   ├── ConfigManager.java           # carga/guardado de configuracion
│   │   │   ├── OmniStreamForceConfig.java       # modelo de configuracion
│   │   │   └── ProfileLoader.java           # perfiles predefinidos
│   │   └── util/
│   │       └── RandomUtils.java
│   └── src/test/java/...
│
├── omnistreamforce-domains/               # modulos de dominios especificos
│   ├── healthcare/
│   │   └── src/main/java/de/omnistreamforce/domain/healthcare/
│   │       ├── HealthcareGenerator.java
│   │       ├── PatientEvent.java
│   │       ├── AdmissionEvent.java
│   │       └── MedicalRecordEvent.java
│   ├── ecommerce/
│   │   └── src/main/java/de/omnistreamforce/domain/ecommerce/
│   │       ├── EcommerceGenerator.java
│   │       ├── OrderEvent.java
│   │       ├── PaymentEvent.java
│   │       └── InventoryEvent.java
│   ├── energy/
│   ├── autos/
│   └── highway/
│
├── omnistreamforce-cli/                   # interfaz de linea de comandos
│   └── src/main/java/de/omnistreamforce/cli/
│       ├── OmniStreamForceApp.java         # punto de entrada (main)
│       ├── commands/
│       │   ├── InteractiveCommand.java  # modo interactivo guiado
│       │   ├── BatchCommand.java        # modo batch con config file
│       │   ├── ConnectCommand.java      # conectar y validar cluster
│       │   └── ListDomainsCommand.java  # listar dominios disponibles
│       ├── interactive/
│       │   ├── InteractiveSession.java   # sesion interactiva con prompts
│       │   ├── DomainSelector.java        # seleccion de dominio
│       │   ├── TopicSelector.java         # seleccion interactiva de topics del cluster
│       │   ├── SchemaReviewer.java         # revision de esquema propuesto
│       │   └── PublishingDashboard.java    # dashboard en vivo de publicacion multi-topic
│       └── console/
│           ├── ConsoleRenderer.java        # renderizado de tablas, colores
│           └── ProgressIndicator.java      # indicador de progreso
│
├── omnistreamforce-ai/                    # modulo de integracion con IA
│   └── src/main/java/de/omnistreamforce/ai/
│       ├── prompts/
│       │   ├── PromptTemplates.java        # plantillas de prompts para LLM
│       │   ├── SchemaProposalPrompt.java
│       │   └── DataGenerationPrompt.java
│       └── parser/
│           └── SchemaParser.java           # parseo de respuestas de LLM
│
├── config/
│   ├── omnistreamforce-default.yaml
│   └── profiles/
│       ├── healthcare-fast.yaml
│       ├── ecommerce-burst.yaml
│       └── energy-low-frequency.yaml
│
├── docker/
│   ├── Dockerfile
│   └── docker-compose.yml              # Kafka + Zookeeper para desarrollo
│
├── install/                            # scripts de instalacion multi-plataforma
│   ├── windows/
│   │   ├── install.ps1                 # script PowerShell para Windows
│   │   └── omnistreamforce.json       # manifest para Scoop
│   ├── linux/
│   │   ├── install.sh                 # script bash para Linux
│   │   └── omnistreamforce.sh          # comando para SDKMAN!
│   ├── macos/
│   │   ├── install.sh                 # script bash para macOS
│   │   └── omnistreamforce.rb         # formula para Homebrew tap
│   └── omnistreamforce.bat            # wrapper para Windows (PATH)
│
├── release/
│   ├── jreleaser.yml                  # config de JReleaser para multi-OS packaging
│   └── graalvm/                        # configs de native-image por plataforma
│       ├── windows-config.json
│       ├── linux-config.json
│       └── macos-config.json
│
└── docs/
    ├── README.md
    ├── domains.md                       # documentacion de dominios
    └── configuration.md
```

---

## Modelo de Datos Base

```java
// Evento generico que todos los dominios deben producir
public class Event {
    String eventId;           // UUID
    String eventType;         // NORMAL, ERROR, WARNING, INFO
    String domain;            // healthcare, ecommerce, ...
    String source;            // origen del evento
    long timestamp;           // epoch millis
    String schemaVersion;     // version del esquema
    Map<String, Object> payload;   // datos especificos del dominio
    Map<String, String> metadata;  // metadatos adicionales
    String traceId;           // para trazabilidad
    String correlationId;     // correlacion entre eventos
}

// Definicion de esquema para un dominio
public class EventSchema {
    String domain;
    String name;
    String description;
    List<FieldDefinition> fields;
    List<EventSpec> eventTypes;     // tipos de eventos que produce
    ErrorSpec errorSpec;            // especificacion de errores
}

public class FieldDefinition {
    String name;
    String type;              // string, int, double, boolean, enum, datetime
    boolean required;
    String fakerExpression;   // expresion para generar datos
    Object defaultValue;
    List<String> enumValues;  // si es enum
    String description;
}

// Mapeo de dominio a topic para multi-topic routing
public class TopicMapping {
    String domain;            // healthcare, ecommerce, ...
    String topicName;         // nombre del topic de destino
    String errorTopicName;    // topic separado para errores (opcional, null = mismo topic)
    int partitions;           // particiones del topic (si se crea nuevo)
    short replicationFactor;  // factor de replicacion (si se crea nuevo)
    boolean autoCreate;       // crear topic si no existe
}

// Configuracion de routing que agrupa todos los mapeos
public class DomainTopicConfig {
    List<TopicMapping> mappings;   // lista de mapeos dominio -> topic
    boolean separateErrorTopics;    // publicar errores en topic separado
    String errorTopicSuffix;        // sufijo para topics de error (ej: "-errors")
}
```

---

## Flujo Interactivo del CLI

```
┌─────────────────────────────────────────────────────────┐
│                    OmniStreamForce v1.0                      │
│        Data Stream Generator for Apache Kafka            │
│            Windows | Linux | macOS                        │
└─────────────────────────────────────────────────────────┘

Step 1: Kafka Connection
────────────────────────
? Cluster type: [1] Local/Self-hosted  [2] AWS MSK  [3] Confluent Cloud: 2

  AWS MSK Configuration:
  ? Bootstrap servers: b-1.cluster.abc123.c2.kafka.us-east-1.amazonaws.com:9094
  ? Authentication: [1] SASL/SCRAM  [2] IAM (MSK)  [3] mTLS: 2
  ? AWS region: us-east-1
  ? Testing connection... [OK]
  ? Cluster: msk-cluster-prod (3 brokers, v3.6.0, AWS IAM auth)

Step 2: Topic Selection (Interactive)
──────────────────────────────────────
? Available topics in cluster (12 found):
  [1]  healthcare-events        (3 partitions)
  [2]  healthcare-alerts        (6 partitions)
  [3]  ecommerce-orders          (3 partitions)
  [4]  ecommerce-payments        (3 partitions)
  [5]  energy-readings           (1 partition)
  [6]  highway-traffic           (6 partitions)
  ...
  [N]  + Create new topic

? Available actions: select existing | create new | auto-map by domain

Step 3: Domain & Topic Mapping
───────────────────────────────
? How many domains to publish? 2

  Domain 1 of 2:
  ? Select domain: [2] Ecommerce
  ? Event schema proposed by AI - review? (Y/n): Y
  ? Target topic: [3] ecommerce-orders
  ? Error topic: [4] ecommerce-payments (or same as target)
  ? Events per second: 50
  ? Error rate (%): 15

  Domain 2 of 2:
  ? Select domain: [1] Healthcare
  ? Event schema proposed by AI - review? (Y/n): Y
  ? Target topic: [1] healthcare-events
  ? Error topic: [2] healthcare-alerts
  ? Events per second: 20
  ? Error rate (%): 10

  Summary:
  ┌─────────────┬─────────────────────┬─────────────────────┬─────┬────────┐
  │ Domain      │ Topic               │ Error Topic         │ EPS │ Err %  │
  ├─────────────┼─────────────────────┼─────────────────────┼─────┼────────┤
  │ Ecommerce   │ ecommerce-orders   │ ecommerce-payments  │  50 │   15%  │
  │ Healthcare  │ healthcare-events  │ healthcare-alerts   │  20 │   10%  │
  └─────────────┴─────────────────────┴─────────────────────┴─────┴────────┘

  ? Start publishing? (Y/n): Y

Step 4: Configuration (global)
───────────────────────────────
? Event format: [JSON/Avro/Protobuf]: JSON
? Publishing mode: (steady) [steady/burst/spike/ramp]: steady
? Duration: [unlimited/60s/300s/custom]: unlimited
? Key strategy: [random/entityId/roundRobin]: entityId

Step 5: Publishing Dashboard (Multi-Topic)
──────────────────────────────────────────
[STARTING] Connecting to producer...
[OK] Producer ready. 4 topics verified across 2 domains.

  ┌─ Ecommerce → ecommerce-orders ────────────────────────┐
  │  1,245 events sent | 187 errors (to ecommerce-payments)│
  │  50 evt/s | latency: 12ms                              │
  │  ████████████████████████████████████░░░░░░  87%      │
  └────────────────────────────────────────────────────────┘

  ┌─ Healthcare → healthcare-events ──────────────────────┐
  │    498 events sent | 50 errors (to healthcare-alerts)  │
  │  20 evt/s | latency: 8ms                               │
  │  ████████████████████░░░░░░░░░░░░░░░░░░░░░░  58%      │
  └────────────────────────────────────────────────────────┘

  TOTAL: 1,743 events | 237 errors | 70 evt/s combined

  [P] Pause all  [S] Stop  [C] Change speed  [E] Error rate
  [D] Domain view  [T] Topic view  [Q] Quit
```

---

## Prompts de Implementacion por Fases

> Cada fase esta disenada como un prompt autocontenido que puede entregarse a un asistente de codigo o desarrollador. Las fases deben ejecutarse secuencialmente.

---

### FASE 1: Fundacion y Estructura del Proyecto

**Objetivo**: Crear la estructura base del proyecto Maven multi-modulo con las dependencias necesarias y el esqueleto de clases principales.

**Prompt**:

```
Crea un proyecto Maven multi-modulo llamado "omnistreamforce" en Java 21 con la siguiente estructura de modulos:
- omnistreamforce-core (nucleo: modelos, interfaces, motor, kafka producer)
- omnistreamforce-domains (sub-modulos: healthcare, ecommerce, energy, autos, highway)
- omnistreamforce-cli (interfaz de linea de comandos)
- omnistreamforce-ai (integracion con LLM)

Dependencias clave (en el pom.xml padre):
- org.apache.kafka:kafka-clients:3.7.0
- info.picocli:picocli:4.7.6
- com.fasterxml.jackson.core:jackson-databind:2.17.0
- org.yaml:snakeyaml:2.2
- net.datafaker:datafaker:2.0.2
- org.slf4j:slf4j-api:2.0.12
- ch.qos.logback:logback-classic:1.5.3
- software.amazon.msk:aws-msk-iam-auth:2.1.0 (AWS MSK IAM auth)
- software.amazon.awssdk:kafka:2.25.0 (AWS MSK API para descubrir brokers)
- org.junit.jupiter:junit-jupiter:5.10.2 (test)
- org.testcontainers:kafka:1.19.7 (test)

Configura:
- Java 21 con records y pattern matching habilitados
- Plugin maven-shade para fat JAR en el modulo CLI
- Plugin maven-compiler con annotation processing para Picocli

Crea las siguientes clases base en omnistreamforce-core:
1. de.omnistreamforce.core.Event (record) - modelo base con campos: eventId, eventType, domain, source, timestamp, schemaVersion, payload (Map), metadata (Map), traceId, correlationId
2. de.omnistreamforce.core.EventType (enum) - NORMAL, ERROR, WARNING, INFO
3. de.omnistreamforce.core.Severity (enum) - LOW, MEDIUM, HIGH, CRITICAL
4. de.omnistreamforce.domain.DomainGenerator (interface) - metodos: getDomainName(), getSupportedEventTypes(), generateEvent(EventType), generateErrorEvent()
5. de.omnistreamforce.domain.DomainRegistry (class) - registro de dominios disponibles con descubrimiento automatico
6. de.omnistreamforce.routing.TopicMapping (record) - domain, topicName, errorTopicName, partitions, replicationFactor, autoCreate
7. de.omnistreamforce.routing.DomainTopicConfig (record) - List<TopicMapping> mappings, boolean separateErrorTopics, String errorTopicSuffix
8. de.omnistreamforce.routing.TopicRouter (interface) - route(Event event) -> String topicName; mapea dominio a topic segun config

Crea un OmniStreamForceApp.java basico en omnistreamforce-cli que imprima un banner ASCII y un mensaje de bienvenida usando Picocli.
```

**Criterios de Aceptacion**:
- `mvn clean compile` compila sin errores
- `java -jar omnistreamforce-cli.jar` muestra el banner
- Estructura de modulos correcta y compilable

---

### FASE 2: Modelo de Dominios y Esquemas

**Objetivo**: Implementar el sistema de esquemas de eventos y los primeros dos dominios (healthcare y ecommerce) como referencia.

**Prompt**:

```
Utilizando el proyecto OmniStreamForce creado en la Fase 1, implementa el sistema de esquemas y los dominios healthcare y ecommerce.

1. En omnistreamforce-core, crea:
   - de.omnistreamforce.core.EventSchema (record) - dominio, nombre, descripcion, List<FieldDefinition>, List<EventSpec>, ErrorSpec
   - de.omnistreamforce.core.FieldDefinition (record) - name, type, required, fakerExpression, defaultValue, enumValues, description
   - de.omnistreamforce.core.EventSpec (record) - typeName, eventType, description, List<FieldDefinition> fields
   - de.omnistreamforce.core.ErrorSpec (record) - errorTypes (List<String>), errorRate (double), errorFields (List<String>)

2. En omnistreamforce-domains/healthcare, implementa HealthcareGenerator implements DomainGenerator:
   - Eventos normales: PatientAdmission, VitalSignsRecorded, MedicationAdministered, LabResultReady, PatientDischarged
   - Eventos de error: PatientIdMismatch, CriticalVitalSigns, MedicationConflict, LabResultAnomaly
   - Usar DataFaker para nombres, IDs, fechas, valores medicicos
   - Payload con campos realistas: patientId, patientName, age, gender, admissionType, vitalSigns (heartRate, bloodPressure, temperature, oxygenSaturation), medications, labResults

3. En omnistreamforce-domains/ecommerce, implementa EcommerceGenerator implements DomainGenerator:
   - Eventos normales: OrderCreated, PaymentProcessed, InventoryUpdated, OrderShipped, OrderCancelled
   - Eventos de error: PaymentFailed, InventoryOutOfStock, FraudDetected, ShippingFailed
   - Payload con campos: orderId, customerId, customerName, products (List), totalAmount, currency, paymentMethod, shippingAddress, status

4. Cada dominio debe exponer su EventSchema via metodo getSchema().

5. Crea tests unitarios para ambos generadores verificando:
   - Que los eventos generados tienen todos los campos requeridos
   - Que los eventos de error se generan correctamente
   - Que los datos generados son consistentes (formatos validos)
```

**Criterios de Aceptacion**:
- HealthcareGenerator genera eventos con datos realistas
- EcommerceGenerator genera eventos con datos realistas
- Ambos generan eventos de error con campos coherentes
- Tests unitarios pasan

---

### FASE 3: Motor de Generacion y Planificador

**Objetivo**: Implementar el motor de generacion que orquesta la creacion de eventos, la inyeccion de errores y el control de frecuencia/velocidad.

**Prompt**:

```
Utilizando el proyecto OmniStreamForce, implementa el motor de generacion en omnistreamforce-core.

1. de.omnistreamforce.engine.GenerationEngine:
   - Recibe un DomainGenerator, una configuracion de generacion, un EventSerializer y un TopicRouter
   - Soporta multiples DomainGenerators activos simultaneamente (uno por dominio/topic)
   - Cada dominio tiene su propia configuracion de EPS, errorRate y topic de destino
   - Ejecuta un bucle de generacion que:
      a. Decide aleatoriamente si el evento es normal o error (basado en errorRate del dominio)
      b. Si es error, selecciona un tipo de error aleatorio de los disponibles
      c. Si es normal, selecciona un tipo de evento aleatorio
      d. Genera el evento usando el DomainGenerator correspondiente
      e. Lo serializa y lo pasa al EventPublisher con el topic de destino resuelto por TopicRouter
   - Expone metodos: start(), pause(), resume(), stop(), addDomain(DomainGenerator, GenerationConfig, TopicMapping), removeDomain(String domainName)
   - Mantiene estadisticas por dominio y globales: totalEvents, totalErrors, eventsPerSecond, uptime, perTopicStats

2. de.omnistreamforce.engine.EventScheduler:
   - Controla la frecuencia de publicacion (events per second)
   - Modos de publicacion:
     - STEADY: ritmo constante
     - BURST: rafagas periodicas (ej: 100 eventos cada 10s)
     - SPIKE: picos aleatorios
     - RAMP: incremento gradual hasta maximo
   - Usa ScheduledExecutorService para controlar el timing
   - Permite ajuste dinamico de velocidad en tiempo de ejecucion

3. de.omnistreamforce.engine.ErrorInjector:
   - Recibe la tasa de error configurada (0-100%)
   - Selecciona aleatoriamente que eventos seran errores
   - Asegura que al menos un tipo de error de cada tipo se genere en algun momento
   - Permite configurar severidad de errores (distribucion LOW/MEDIUM/HIGH/CRITICAL)
   - Genera errores realistas: campos faltantes, valores fuera de rango, datos invalidos, timeouts simulados

4. de.omnistreamforce.engine.GenerationConfig (record):
   - eventsPerSecond, errorRate, publishingMode (STEADY/BURST/SPIKE/RAMP), durationSeconds (0=ilimitado), keyStrategy (RANDOM/ENTITY_ID/ROUND_ROBIN), burstSize, rampTargetEPS, domainName, topicName, errorTopicName, keyField

5. de.omnistreamforce.engine.MultiDomainEngine:
   - Orquesta multiples GenerationEngine (uno por dominio) en paralelo
   - Usa virtual threads (Java 21) para ejecutar cada dominio concurrentemente
   - Agrega estadisticas de todos los dominios en un solo dashboard
   - Permite anadir/quitar dominios en caliente sin detener los demas

5. Crea tests unitarios con mocks del publisher verificando:
   - Que la tasa de eventos se acerca a la configurada
   - Que la tasa de error se acerca a la configurada
   - Que pause/resume funcionan correctamente
   - Que stop detiene limpiamente
   - Que el multi-domain engine genera eventos a multiples topics simultaneamente
   - Que addDomain/removeDomain funciona en caliente
```

**Criterios de Aceptacion**:
- El motor genera eventos a la frecuencia configurada
- Los errores se inyectan segun la tasa configurada
- Los modos STEADY, BURST, SPIKE y RAMP funcionan
- pause/resume/stop funcionan correctamente
- Multi-domain engine publica a multiples topics en paralelo
- addDomain/removeDomain en caliente sin interrumpir otros dominios
- Tests unitarios pasan

---

### FASE 4: Integracion con Kafka Producer

**Objetivo**: Implementar la conexion, configuracion y publicacion de eventos a Kafka con manejo de errores y reintentos.

**Prompt**:

```
Utilizando el proyecto OmniStreamForce, implementa la capa de integracion con Kafka en omnistreamforce-core.

1. de.omnistreamforce.kafka.KafkaConnectionManager:
   - Configura KafkaProducer<String, byte[]> con props configurables
   - Propiedades soportadas: bootstrap.servers, acks, retries, batch.size, linger.ms, buffer.memory, compression.type, max.in.flight.requests.per.connection, client.id
   - Soporte para seguridad: PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL
   - Soporte para AWS MSK:
     * SASL/SCRAM sobre SSL (autenticacion con usuario/contraseña)
     * IAM (AWS MSK) - usa software.amazon.msk.aws-msk-iam-auth library
     * Configuracion automatica de region de AWS y arn del cluster
     * Deteccion automatica de endpoints publicos vs privados de MSK
   - Soporte para Confluent Cloud (SASL/PLAIN con API key/secret)
   - Metodo connect() que valida la conexion listando topics
   - Metodo getClusterInfo() que retorna metadata del cluster (brokers, version, tipo de cluster)
   - Metodo close() con graceful shutdown

2. de.omnistreamforce.kafka.MskClusterConfig:
   - Modelo de configuracion especifica para AWS MSK
   - Campos: bootstrapBrokers (publicos o privados), authType (SCRAM/IAM), awsRegion, mskArn, scramUsername, scramPassword
   - Metodo toKafkaProps() que genera las propiedades del producer para MSK
   - Detecta automaticamente si usar endpoints publicos (con SASL) o privados (VPC)

3. de.omnistreamforce.kafka.KafkaEventPublisher:
   - Implementa interfaz EventPublisher (definir: publish(Event, String topic), publishBatch(List<Event>, String topic))
   - Usa KafkaProducer.send() con callbacks asincronos
   - Soporta publicacion a multiples topics simultaneamente con un solo producer
   - Estrategia de clave (key):
     - RANDOM: UUID aleatorio
     - ENTITY_ID: usa un campo del evento como key (configurable, ej: orderId, patientId)
     - ROUND_ROBIN: contador incremental
   - Callback maneja: exito (log debug), fallo (log error + retry si esta configurado)
   - Metricas por topic y globales: totalSent, totalAcknowledged, totalFailed, avgLatencyMs, perTopicStats (Map<String, TopicStats>)
   - Retry configurable: maxRetries, retryBackoffMs

4. de.omnistreamforce.kafka.KafkaTopicManager:
   - verifyOrCreateTopic(String topicName, int partitions, short replicationFactor)
   - Usa AdminClient para crear topic si no existe
   - Verifica configuracion del topic existente
   - listTopics() -> lista todos los topics disponibles con sus particiones y configuracion
   - getTopicInfo(String topicName) -> detalles de un topic especifico
   - listTopicsWithPartitions() -> lista topics con numero de particiones para seleccion interactiva en CLI

4. Crea tests de integracion con Testcontainers:
   - Levanta un KafkaContainer en @Test
   - Verifica publicacion de eventos a un topic
   - Verifica publicacion simultanea a multiples topics
   - Verifica creacion de topic
   - Verifica listTopics() devuelve topics con particiones
   - Verifica metricas de publicacion (globales y por topic)
   - Verifica manejo de errores cuando el broker no esta disponible
   - Test de configuracion MskClusterConfig.toKafkaProps() genera props correctas (sin conectarse a AWS)
```

**Criterios de Aceptacion**:
- Se conecta a un cluster de Kafka local y valida la conexion
- Se conecta a AWS MSK con IAM y SASL/SCRAM
- Publica eventos asincronamente con callbacks a multiples topics
- Crea topics automaticamente si no existen
- listTopics() lista topics con particiones para seleccion interactiva
- Las metricas de publicacion son correctas (globales y por topic)
- MskClusterConfig genera propiedades correctas del producer
- Tests de integracion con Testcontainers pasan

---

### FASE 5: Serializadores Multi-formato

**Objetivo**: Implementar serializadores para JSON, Avro y Protobuf.

**Prompt**:

```
Utilizando el proyecto OmniStreamForce, implementa los serializadores de eventos en omnistreamforce-core/serializer.

1. de.omnistreamforce.serializer.EventSerializer (interface):
   - byte[] serialize(Event event)
   - Event deserialize(byte[] data)
   - String getFormatName()

2. de.omnistreamforce.serializer.JsonEventSerializer:
   - Usa Jackson ObjectMapper
   - Pretty printing configurable
   - Incluye schemaVersion en el payload
   - Maneja tipos complejos (nested objects, arrays, enums)
   - Timestamp en ISO-8601 o epoch (configurable)

3. de.omnistreamforce.serializer.AvroEventSerializer:
   - Usa Apache Avro
   - Genera schema dinamicamente desde EventSchema del dominio
   - Soporta Schema Registry (Confluent) opcional
   - Compatible con formato Confluent (magic byte + schema id + avro data)

4. de.omnistreamforce.serializer.ProtobufEventSerializer:
   - Define mensajes .proto dinamicamente o usa proto generico
   - Serializa a formato binario protobuf

5. de.omnistreamforce.serializer.SerializerFactory:
   - Factory method: create(String format) -> EventSerializer
   - Formatos soportados: JSON, AVRO, PROTOBUF

6. Tests para cada serializador:
   - Serializa un evento y lo deserializa verificando round-trip
   - Verifica que el formato es legible por consumidores externos
```

**Criterios de Aceptacion**:
- JSON serializa/deserializa correctamente con round-trip
- Avro genera schema dinamico y serializa correctamente
- Protobuf serializa correctamente
- SerializerFactory crea la implementacion correcta

---

### FASE 6: Dominios Adicionales (Energy, Autos, Highway)

**Objetivo**: Implementar los dominios restantes con eventos realistas.

**Prompt**:
```
Utilizando el proyecto OmniStreamForce, implementa tres dominios adicionales en omnistreamforce-domains siguiendo el patron establecido en la Fase 2.

1. omnistreamforce-domains/energy - EnergyGenerator:
   Eventos normales:
   - PowerGeneration: plantId, source (solar/wind/hydro/nuclear/gas), outputMW, efficiency, timestamp
   - GridStatus: gridId, frequency, voltage, load, stability
   - MeterReading: meterId, customerId, consumptionkWh, timestamp
   - PriceUpdate: market, pricePerMWh, currency, period
   Eventos de error:
   - PowerOutage: plantId, affectedAreas, cause, severity
   - GridFrequencyDeviation: gridId, frequency, deviation, threshold
   - EquipmentFailure: plantId, equipmentType, failureType, severity

2. omnistreamforce-domains/autos - AutosGenerator:
   Eventos normales:
   - VehicleTelemetry: vehicleId, speed, rpm, fuelLevel, engineTemp, gpsLocation, odometer
   - VehicleStatus: vehicleId, status (moving/parked/charging/maintenance), batteryLevel, tirePressure
   - TripEvent: tripId, vehicleId, startLocation, endLocation, distance, duration
   - MaintenanceAlert: vehicleId, component, alertType, mileage
   Eventos de error:
   - EngineOverheat: vehicleId, temperature, threshold
   - BrakeFailure: vehicleId, brakeType, severity
   - BatteryCritical: vehicleId, batteryLevel, threshold
   - GPSLoss: vehicleId, lastKnownLocation

3. omnistreamforce-domains/highway - HighwayGenerator:
   Eventos normales:
   - TrafficFlow: segmentId, vehicleCount, avgSpeed, density
   - VehiclePassage: tollId/segmentId, plate, vehicleType, speed, lane
   - IncidentReport: incidentId, type (accident/construction/weather), location, severity, lanesAffected
   - TollPayment: tollId, plate, amount, paymentMethod, vehicleClass
   Eventos de error:
   - TrafficJam: segmentId, vehicleCount, avgSpeed, threshold
   - SpeedViolation: segmentId, plate, speed, limit, violationType
   - SensorMalfunction: sensorId, segmentId, malfunctionType

Cada generador debe:
- Implementar DomainGenerator
- Usar DataFaker para datos realistas
- Exponer EventSchema completo
- Generar eventos con timestamps coherentes
- Incluir trazabilidad (traceId, correlationId) en eventos relacionados
- Tener tests unitarios que validen estructura de eventos
```

**Criterios de Aceptacion**:
- Los 3 dominios generan eventos con datos realistas
- Cada dominio tiene eventos normales y de error
- Los esquemas son coherentes y completos
- Tests unitarios pasan

---

### FASE 7: Integracion con IA (LLM)

**Objetivo**: Implementar la integracion con LLM para proponer esquemas de eventos para dominios personalizados y generar datos realistas.

**Prompt**:
```
Utilizando el proyecto OmniStreamForce, implementa el modulo omnistreamforce-ai para integracion con LLM.

1. de.omnistreamforce.ai.AIService (interface):
   - String chat(String systemPrompt, String userPrompt)
   - EventSchema proposeSchema(String domainName, String description)
   - Event generateEvent(EventSchema schema, EventType eventType)
   - boolean isAvailable()

2. de.omnistreamforce.ai.OpenAIService (implementacion con OpenAI API):
   - Usa HTTP client nativo de Java 11+ (java.net.http.HttpClient)
   - Configuracion: apiKey, model (gpt-4o/gpt-4o-mini), temperature, maxTokens
   - Endpoint: https://api.openai.com/v1/chat/completions
   - Implementa rate limiting y retry con backoff

3. de.omnistreamforce.ai.OllamaService (implementacion local con Ollama):
   - Endpoint: http://localhost:11434/api/chat
   - Modelos: llama3, mistral, etc.
   - Sin necesidad de API key

4. de.omnistreamforce.ai.prompts.PromptTemplates:
   - SCHEMA_PROPOSAL: "You are a data architect. Propose a Kafka event schema for the domain '{domain}'. Include 3-5 normal event types and 2-3 error event types. For each event, define fields with types. Respond in JSON format matching this structure: {schema}."
   - DATA_GENERATION: "Generate realistic sample data for a {eventType} event in the {domain} domain. Fields: {fields}. Return a JSON object with the field values."
   - DOMAIN_DISCOVERY: "Given the domain name '{name}', describe 5 key entities and their relationships. Suggest appropriate Kafka topic names."

5. de.omnistreamforce.ai.SchemaProposer:
   - Usa AIService para proponer esquemas para dominios no predefinidos
   - Parsea la respuesta del LLM a EventSchema
   - Valida que el esquema tiene los campos minimos
   - Permite al usuario editar/refinar el esquema propuesto
   - Cachea esquemas propuestos para no llamar al LLM repetidamente

6. de.omnistreamforce.ai.parser.SchemaParser:
   - Parsea respuesta JSON del LLM a EventSchema
   - Maneja respuestas con markdown code blocks
   - Maneja respuestas parcialmente validas
   - Normaliza tipos (string->String, int->Integer, etc.)

7. Configuracion del AI service:
   - omnistreamforce.ai.enabled (boolean)
   - omnistreamforce.ai.provider (openai/ollama)
   - omnistreamforce.ai.api-key
   - omnistreamforce.ai.model
   - omnistreamforce.ai.base-url (para Ollama o proxies)

8. Tests con respuestas mockeadas del LLM verificando parseo correcto.
```

**Criterios de Aceptacion**:
- OpenAIService conecta y obtiene respuestas del LLM
- OllamaService conecta con instancia local de Ollama
- SchemaProposer genera esquemas validos parseables
- SchemaParser maneja formatos variados de respuesta
- Fallback graceful cuando IA no esta disponible

---

### FASE 8: CLI Interactivo Completo

**Objetivo**: Implementar el CLI interactivo completo con flujo guiado, dashboard en vivo y controles.

**Prompt**:
```
Utilizando el proyecto OmniStreamForce, implementa el CLI interactivo completo en omnistreamforce-cli usando Picocli.

1. de.omnistreamforce.cli.OmniStreamForceApp (comando principal):
   - Subcomandos: interactive (default), batch, connect, list-domains, list-topics
   - Opciones globales: --config <file>, --verbose, --ai-enabled, --ai-provider
   - Banner ASCII art al iniciar (mostrar plataforma detectada: Windows/Linux/macOS)

2. de.omnistreamforce.cli.commands.InteractiveCommand:
   Flujo interactivo paso a paso:

   STEP 1 - Kafka Connection:
   - Preguntar tipo de cluster: [1] Local/Self-hosted [2] AWS MSK [3] Confluent Cloud [4] Custom
   - Si Local/Self-hosted:
     * Pedir bootstrap servers (default localhost:9092)
     * Pedir security protocol (default PLAINTEXT)
     * Si SSL/SASL: pedir credenciales adicionales
   - Si AWS MSK:
     * Pedir bootstrap servers (endpoints publicos o privados)
     * Pedir tipo de autenticacion: [1] SASL/SCRAM [2] IAM [3] mTLS
     * Si SASL/SCRAM: pedir usuario y contraseña
     * Si IAM: pedir AWS region y profile (default: default)
     * Detectar automaticamente credenciales de AWS desde ~/.aws/credentials
     * Opcion de usar AWS MSK API para descubrir bootstrap brokers por ARN
   - Si Confluent Cloud:
     * Pedir bootstrap servers
     * Pedir API key y secret
   - Validar conexion y mostrar info del cluster (brokers, version, tipo)
   - Listar topics existentes con numero de particiones

   STEP 2 - Topic Selection (Interactive):
   - Mostrar lista de topics disponibles en el cluster (con particiones)
   - Permitir al usuario:
     * Seleccionar topic(s) existente(s)
     * Crear topic(s) nuevo(s) (nombre, particiones, replication factor)
     * Auto-mapeo: generar nombres de topic automaticamente por dominio (ej: {domain}-events)
   - Guardar la seleccion para usar en el mapeo dominio -> topic

   STEP 3 - Domain & Topic Mapping:
   - Preguntar cuantos dominios publicar (1 a N)
   - Por cada dominio:
     a. Seleccionar dominio (registrados + custom via IA)
     b. Si custom, usar IA para proponer esquema -> Schema Review
     c. Seleccionar topic de destino (de la lista del Step 2 o crear nuevo)
     d. Seleccionar topic de errores (mismo topic, otro existente, o crear nuevo con sufijo "-errors")
     e. Configurar EPS, errorRate especifico para este dominio
   - Mostrar tabla resumen de mapeos dominio -> topic
   - Permitir editar mapeos antes de continuar

   STEP 4 - Schema Review (por cada dominio seleccionado):
   - Mostrar esquema de eventos propuesto (tipos normales y de error)
   - Permitir: aceptar, rechazar, editar (campos, tipos, faker expressions)
   - Si IA esta activa, permitir pedir refinamiento

   STEP 5 - Global Publishing Configuration:
   - Event format (JSON/Avro/Protobuf, default: JSON)
   - Publishing mode (steady/burst/spike/ramp, default: steady)
   - Duration (unlimited/seconds, default: unlimited)
   - Key strategy (random/entityId/roundRobin, default: random)

   STEP 6 - Publishing Dashboard (Multi-Topic):
   - Mostrar dashboard en vivo con seccion por cada dominio/topic activo:
     * Contador de eventos enviados (por topic)
     * Contador de errores (por topic, mostrar si van a topic separado)
     * EPS actual (por dominio y total combinado)
     * Latencia promedio (por topic)
     * Barra de progreso (si hay duracion limitada)
     * Indicador de estado del producer
   - Panel de resumen con totales agregados
   - Controles interactivos:
     * [P] Pause all / [R] Resume all
     * [P+domain] Pause specific domain
     * [S] Stop all
     * [C] Change speed (ajustar EPS global o por dominio)
     * [E] Adjust error rate (global o por dominio)
     * [D] Domain view (detalle por dominio)
     * [T] Topic view (detalle por topic)
     * [A] Add domain on the fly (añadir nuevo dominio en caliente)
     * [X] Remove domain on the fly
     * [Q] Quit

3. de.omnistreamforce.cli.interactive.TopicSelector:
   - Usa KafkaTopicManager.listTopicsWithPartitions() para listar topics
   - Renderiza tabla de topics con: nombre, particiones, configuracion
   - Permite seleccion multiple de topics
   - Permite crear topic nuevo con validacion de nombre
   - Permite buscar/filtrar topics por nombre
   - Cachea la lista para no llamar al cluster en cada seleccion

4. de.omnistreamforce.cli.commands.BatchCommand:
   - Ejecuta desde archivo de configuracion YAML sin interaccion
   - Soporta configuracion multi-dominio/multi-topic en el YAML
   - Opciones: --config <file>, --dry-run (genera sin publicar)
   - Util para CI/CD y automatizacion

5. de.omnistreamforce.cli.console.ConsoleRenderer:
   - Tablas con colores ANSI
   - ASCII art para banner
   - Spinners e indicadores de progreso
   - Deteccion de soporte ANSI (fallback a texto plano)
   - Deteccion de plataforma (Windows cmd/PowerShell, Linux, macOS Terminal)
   - Limpiar pantalla entre pasos (compatible con Windows y Unix)

6. de.omnistreamforce.cli.interactive.PublishingDashboard:
   - Thread separado que refresca el dashboard cada 500ms
   - Lee estadisticas del MultiDomainEngine (por dominio y globales)
   - Renderiza panel por cada dominio/topic activo
   - Maneja input del teclado sin bloquear (usando Thread o libreria)
   - Formateo de numeros (K, M para miles/millones)
   - Compatible con terminales de Windows, Linux y macOS

7. de.omnistreamforce.cli.interactive.DomainSelector:
   - Descubre dominios disponibles via DomainRegistry
   - Si IA activa, anade opcion "Custom (AI-generated)"
   - Permite buscar/filtrar dominios por nombre
   - Permite seleccion multiple de dominios para multi-topic publishing

8. Manejo de senales (SIGINT/Ctrl+C):
   - Graceful shutdown de todos los producers activos
   - flush de mensajes en buffer de todos los topics
   - Mostrar resumen final de estadisticas por dominio y globales
```

**Criterios de Aceptacion**:
- El flujo interactivo guia al usuario por todos los pasos
- Conexion a clusters local, AWS MSK (IAM y SASL/SCRAM) y Confluent Cloud
- Topic selection interactivo lista topics reales del cluster con particiones
- Multi-domain/multi-topic mapping permite publicar a multiples topics simultaneamente
- El dashboard muestra estadisticas en vivo por dominio y globales
- Los controles (pause/stop/speed/add-domain/remove-domain) funcionan en tiempo de ejecucion
- Batch mode ejecuta desde YAML multi-topic sin interaccion
- Ctrl+C hace shutdown graceful de todos los producers
- CLI funciona en Windows, Linux y macOS

---

### FASE 9: Sistema de Configuracion y Perfiles

**Objetivo**: Implementar el sistema de configuracion con perfiles guardables y cargables.

**Prompt**:
```
Utilizando el proyecto OmniStreamForce, implementa el sistema de configuracion en omnistreamforce-core/config.

1. de.omnistreamforce.config.OmniStreamForceConfig (record/POJO):
   - KafkaSection: bootstrapServers, securityProtocol, saslMechanism, sslConfig, producerProps (acks, retries, etc.), clusterType (LOCAL/MSK/CONFLUENT), mskConfig (MskClusterConfig embedido)
   - DomainTopicMappingSection: List<DomainTopicMapping> mappings (cada uno con: domainName, topicName, errorTopicName, eventsPerSecond, errorRate, keyStrategy, keyField), separateErrorTopics, errorTopicSuffix
   - GenerationSection: publishingMode (global), durationSeconds, burstSize, rampTargetEPS
   - SerializationSection: format (JSON/AVRO/PROTOBUF), schemaRegistryUrl, prettyPrint
   - AISection: enabled, provider, apiKey, model, baseUrl, temperature
   - TopicSection: partitions, replicationFactor, autoCreate (defaults para topics nuevos)

2. de.omnistreamforce.config.ConfigManager:
   - load(File yamlFile) -> OmniStreamForceConfig
   - save(OmniStreamForceConfig, File)
   - loadDefault() -> configuracion por defecto
   - merge(OmniStreamForceConfig base, OmniStreamForceConfig override) -> combinar configs
   - validate(OmniStreamForceConfig) -> lista de errores de validacion

3. de.omnistreamforce.config.ProfileLoader:
   - Perfiles predefinidos en config/profiles/*.yaml
   - listProfiles() -> lista de perfiles disponibles
   - loadProfile(String name) -> OmniStreamForceConfig
   - saveProfile(String name, OmniStreamForceConfig) -> guarda perfil personalizado en ~/.omnistreamforce/profiles/

4. Crea perfiles de ejemplo en config/profiles/:
   - healthcare-fast.yaml: 100 EPS, 5% errors, steady mode, topic: healthcare-events
   - ecommerce-burst.yaml: 500 EPS burst, 15% errors, burst mode, topic: ecommerce-orders
   - energy-low-frequency.yaml: 1 EPS, 2% errors, steady mode, topic: energy-readings
   - highway-spike.yaml: 50 EPS, 20% errors, spike mode, topic: highway-traffic
   - development.yaml: 5 EPS, 50% errors, steady mode (para testing)
   - multi-domain-msk.yaml: 2 dominios (ecommerce + healthcare) a AWS MSK, topics separados, error topics dedicados
   - aws-msk-iam.yaml: configuracion de AWS MSK con IAM, un dominio, topic preexistente

5. Soporte para variables de entorno y placeholders:
   - ${KAFKA_BOOTSTRAP_SERVERS} se reemplaza por env var
   - ${env:OPENAI_API_KEY} para secretos
   - ${env:AWS_REGION} y ${env:AWS_ACCESS_KEY_ID} para MSK
   - Defaults cuando la env var no existe

6. Configuracion en ~/.omnistreamforce/omnistreamforce.yaml como configuracion global persistente.

7. Ejemplo de YAML multi-dominio/multi-topic para referencia:
```yaml
kafka:
  clusterType: MSK
  bootstrapServers: "b-1.cluster.xxx.c2.kafka.us-east-1.amazonaws.com:9094"
  mskConfig:
    authType: IAM
    awsRegion: us-east-1
domainTopicMappings:
  - domain: ecommerce
    topicName: ecommerce-orders
    errorTopicName: ecommerce-payments
    eventsPerSecond: 50
    errorRate: 15
    keyStrategy: ENTITY_ID
    keyField: orderId
  - domain: healthcare
    topicName: healthcare-events
    errorTopicName: healthcare-alerts
    eventsPerSecond: 20
    errorRate: 10
    keyStrategy: ENTITY_ID
    keyField: patientId
separateErrorTopics: true
generation:
  publishingMode: steady
  durationSeconds: 0
serialization:
  format: JSON
topic:
  partitions: 3
  replicationFactor: 2
  autoCreate: false
```
```

**Criterios de Aceptacion**:
- Carga y guarda YAML correctamente (incluyendo multi-dominio/multi-topic)
- Perfiles predefinidos funcionan (incluyendo multi-domain y MSK)
- Variables de entorno se resuelven (incluyendo AWS)
- Validacion de configuracion detecta errores
- Merge de configuraciones funciona
- MskClusterConfig se serializa/deserializa correctamente en YAML

---

### FASE 10: Docker, Distribucion y Documentacion

**Objetivo**: Empaquetar, containerizar y documentar el proyecto para distribucion.

**Prompt**:
```
Utilizando el proyecto OmniStreamForce, completa la distribucion y documentacion para Windows, Linux y macOS.

1. Dockerfile multi-stage:
   - Stage 1: Maven build con Java 21
   - Stage 2: Runtime con JRE 21 slim
   - ENTRYPOINT: java -jar omnistreamforce-cli.jar
   - Variables de entorno configurables

2. docker-compose.yml:
   - Servicio omnistreamforce (la app)
   - Servicio kafka (confluentinc/cp-kafka o bitnami/kafka en modo KRaft sin zookeeper)
   - Servicio akhq o kafka-ui (UI de Kafka para visualizar)
   - Servicio ollama (para IA local opcional)
   - Volumes para persistencia de datos
   - Network para comunicacion entre servicios

3. Fat JAR con Maven Shade Plugin:
   - Jar ejecutable con todas las dependencias
   - Main-Class: de.omnistreamforce.cli.OmniStreamForceApp
   - Filtrar archivos de firma para evitar conflictos
   - El fat JAR es cross-platform (Windows, Linux, macOS) al ser Java

4. Distribucion Cross-Platform (Windows, Linux, macOS):

   4a. Windows:
   - Script install.ps1 (PowerShell) que:
     * Descarga el fat JAR o binario nativo
     * Lo copia a %LOCALAPPDATA%\OmniStreamForce\
     * Crea un wrapper .bat en el PATH
     * Verifica que Java 21+ este instalado (o descarga JRE)
   - Manifest para Scoop (scoop bucket) en install/windows/omnistreamforce.json
   - Binario nativo opcional via GraalVM native-image (install/windows/graalvm/windows-config.json)
   
   4b. Linux:
   - Script install.sh que:
     * Descarga el fat JAR
     * Lo copia a /usr/local/lib/omnistreamforce/ o ~/.local/bin/
     * Crea wrapper en /usr/local/bin/omnistreamforce
     * Verifica Java 21+ instalado
   - Comando para SDKMAN! (install/linux/omnistreamforce.sh)
   - Package .deb y .rpm opcional via jpackage
   - Binario nativo opcional via GraalVM native-image (install/linux/graalvm/linux-config.json)
   
   4c. macOS:
   - Script install.sh que:
     * Descarga el fat JAR
     * Lo copia a /usr/local/lib/omnistreamforce/ o ~/.local/bin/
     * Crea wrapper en /usr/local/bin/omnistreamforce
     * Verifica Java 21+ instalado o usa Homebrew JDK
   - Formula para Homebrew tap (install/macos/omnistreamforce.rb)
   - Binario nativo opcional via GraalVM native-image (install/macos/graalvm/macos-config.json)

5. JReleaser (release/jreleaser.yml):
   - Configura releases multi-plataforma en GitHub Releases
   - Empaqueta: fat JAR + binarios nativos (GraalVM) para Windows/Linux/macOS
   - Genera: Scoop manifest, Homebrew formula, SDKMAN! candidate
   - Checksums y signatures para cada artefacto
   - Changelog automatico desde commits

6. README.md completo:
   - Que es OmniStreamForce
   - Arquitectura (con diagrama de multi-topic routing)
   - Requisitos (Java 21+, Maven)
   - Instalacion por plataforma:
     * Windows: Scoop o install.ps1
     * Linux: SDKMAN! o install.sh
     * macOS: Homebrew o install.sh
     * Docker: docker-compose up
   - Guia de inicio rapido (docker-compose up + ejecutar CLI)
   - Guia de conexion a AWS MSK (configuracion IAM y SASL/SCRAM)
   - Uso del CLI interactivo (con ejemplos multi-dominio/multi-topic)
   - Uso del modo batch (con ejemplos YAML multi-topic)
   - Dominios disponibles
   - Configuracion de IA (OpenAI y Ollama)
   - Configuracion avanzada (perfiles, YAML, env vars, multi-topic)
   - Guia para crear dominios personalizados
   - Troubleshooting (incluyendo problemas de conexion MSK, cross-platform)

7. docs/domains.md:
   - Documentacion de cada dominio con sus tipos de eventos
   - Campos y tipos de cada evento
   - Ejemplos de payload

8. docs/configuration.md:
   - Todas las opciones de configuracion
   - Ejemplos de YAML completos (incluyendo multi-dominio/multi-topic y AWS MSK)
   - Tabla de variables de entorno
   - Perfiles disponibles
   - Guia de configuracion de AWS MSK (IAM, SASL/SCRAM, endpoints publicos/privados)

9. docs/installation.md:
   - Instrucciones detalladas de instalacion para Windows, Linux y macOS
   - Requisitos previos por plataforma (Java, variables de entorno, PATH)
   - Verificacion de instalacion (omnistreamforce --version)
   - Actualizacion y desinstalacion por plataforma

10. .github/workflows/ci.yml:
    - Build en push en Windows, Linux y macOS runners
    - Tests unitarios y de integracion en las 3 plataformas
    - Build de fat JAR y Docker image
    - Release automatico con JReleaser en tag push (artefactos multi-OS)
```

**Criterios de Aceptacion**:
- `docker-compose up` levanta Kafka + OmniStreamForce
- El fat JAR funciona standalone en Windows, Linux y macOS
- Scripts de instalacion (install.ps1, install.sh) funcionan en cada plataforma
- Binarios nativos (GraalVM) se generan para las 3 plataformas en CI
- Scoop manifest, Homebrew formula y SDKMAN! candidate generados correctamente
- README tiene guia de inicio rapido funcional con instalacion por plataforma
- Documentacion de dominios, configuracion e instalacion completa
- CI construye y testea en las 3 plataformas

---

## Diagrama de Dependencias entre Fases

```
Fase 1 (Fundacion)
  │
  ├──> Fase 2 (Dominos healthcare + ecommerce)
  │      │
  │      └──> Fase 6 (Dominios energy + autos + highway)
  │
  ├──> Fase 3 (Motor de generacion)
  │      │
  │      └──> Fase 4 (Kafka producer) ──> Fase 5 (Serializadores)
  │
  ├──> Fase 7 (Integracion IA) [depende de Fase 2]
  │
  └──> Fase 9 (Configuracion) [depende de Fase 4]
         │
         └──> Fase 8 (CLI completo) [depende de 3,4,5,7,9]
                │
                └──> Fase 10 (Docker + Docs) [depende de todas]
```

## Fases Paralelizables

Las siguientes fases pueden desarrollarse en paralelo tras la Fase 1:
- Fase 2 (dominios) y Fase 3 (motor) y Fase 5 (serializadores) y Fase 7 (IA) y Fase 9 (configuracion)

Las fases Fase 6 (dominios extra) y Fase 8 (CLI) dependen de fases anteriores.

---

## Consideraciones de Diseno

### Extensibilidad de Dominios
- Los dominios son modulos Maven separados
- DomainRegistry descubre dominios via ServiceLoader (Java SPI)
- Para anadir un nuevo dominio: crear modulo, implementar DomainGenerator, registrar en META-INF/services
- Permite anadir dominios sin tocar el core

### Estrategia de Error
- ErrorInjector controla la proporcion de errores
- Errores realistas: campos faltantes, valores fuera de rango, formatos invalidos, nulls donde no deberian
- Distribucion de severidad configurable
- Eventos de error marcados con eventType=ERROR y metadata con detalles del error

### Control de Flujo
- Configuracion granular de la frecuencia de publicacion (ej. 100 mensajes por segundo)
- Duracion configurable: ilimitada, por segundos, o por numero total de eventos
- Cuatro modos de publicacion:
  - **STEADY**: ritmo constante de eventos por segundo
  - **BURST**: rafagas periodicas (ej: 100 eventos cada 10s)
  - **SPIKE**: picos aleatorios que simulan cargas impredecibles
  - **RAMP**: incremento gradual hasta un maximo objetivo
- Ajuste dinamico en tiempo de ejecucion: cambiar EPS, pausar, reanudar sin reiniciar
- EventScheduler usa ScheduledExecutorService con precision de milisegundos
- Backpressure automatico cuando el producer no puede mantener el ritmo (buffer lleno)

### Dual-Stream (Eventos Normales y de Error Simultaneos)
- Generacion simultanea de eventos normales y eventos de error en un mismo flujo de publicacion
- Tasa de error configurable (0-100%) que controla la proporcion de eventos de error
- ErrorInjector decide aleatoriamente cuales eventos seran errores segun la tasa configurada
- Tipos de errores realistas generados automaticamente:
  - **Campos faltantes**: omitir campos requeridos del esquema
  - **Valores fuera de rango**: numeros excediendo limites validos (ej: edad 200, temperatura 999)
  - **Formatos invalidos**: strings con formato incorrecto (ej: email sin @, fechas mal formateadas)
  - **Tipos incorrectos**: string donde se espera int, null donde no se permite
  - **Valores nulos** en campos no-nullable
- Distribucion de severidad configurable (LOW / MEDIUM / HIGH / CRITICAL)
- Eventos de error marcados con eventType=ERROR y metadata con detalles del error (errorType, errorCode, errorMessage, severity)
- Asegura que todos los tipos de error definidos en el dominio se generen en algun momento
- Permite publicar errores a un topic separado (ej: {domain}-errors) o al mismo topic con marcado

### Multi-Topic Routing
- Cada dominio se mapea a un topic de destino independiente (ej: healthcare -> `healthcare-events`, ecommerce -> `ecommerce-orders`)
- TopicRouter resuelve el topic de destino segun el dominio del evento y su tipo (normal o error)
- Soporte para topics de error separados: errores pueden ir a `{domain}-errors` o al mismo topic con marca eventType=ERROR
- Seleccion interactiva de topics: el usuario lista topics reales del cluster y elige destino por dominio
- Auto-mapeo: genera nombres de topic automaticamente por dominio (ej: `{domain}-events`)
- Un solo KafkaProducer publica a multiples topics simultaneamente (el producer de Kafka soporta esto nativamente)
- MultiDomainEngine orquesta multiples dominios en paralelo con virtual threads, cada uno con su propio topic, EPS y errorRate
- Permite anadir/quitar dominios en caliente sin detener los demas
- Dashboard muestra estadisticas por dominio/topic y totales agregados

### Cloud-Native Connectivity (AWS MSK y mas)
- Soporte para multiples tipos de cluster:
  - **Local/Self-hosted**: PLAINTEXT o SSL, sin autenticacion adicional
  - **AWS MSK**: soporte para SASL/SCRAM sobre SSL y IAM (usando aws-msk-iam-auth)
  - **Confluent Cloud**: SASL/PLAIN con API key/secret
  - **Azure Event Hubs**: via compatibilidad Kafka (futuro)
- Para AWS MSK:
  - Deteccion automatica de endpoints publicos vs privados (VPC)
  - Autenticacion IAM usa credenciales de ~/.aws/credentials o environment variables
  - Opcion de descubrir bootstrap brokers via AWS MSK API usando el ARN del cluster
  - MskClusterConfig genera las propiedades del producer correctamente segun el tipo de auth
- Credenciales nunca se almacenan en texto plano: usan variables de entorno o AWS credential chain
- Validacion de conexion lista topics reales antes de continuar con el flujo

### Cross-Platform (Windows, Linux, macOS)
- El fat JAR es cross-platform por naturaleza (Java 21)
- Deteccion automatica del OS al arrancar para ajustar:
  - Comportamiento de terminal (ANSI colors en Windows 10+, cmd clasicos, PowerShell, Unix terminals)
  - Rutas de configuracion (~/.omnistreamforce/ en Unix, %USERPROFILE%\.omnistreamforce\ en Windows)
  - Senales del sistema (SIGINT en Unix, CTRL_BREAK_EVENT en Windows)
- Scripts de instalacion nativos por plataforma:
  - Windows: install.ps1 (PowerShell) + Scoop manifest + wrapper .bat
  - Linux: install.sh + SDKMAN! candidate + packages .deb/.rpm via jpackage
  - macOS: install.sh + Homebrew formula
- Binarios nativos opcionales via GraalVM native-image para cada plataforma (sin necesidad de JRE)
- CI/CD construye y testea en runners de Windows, Linux y macOS simultaneamente
- JReleaser automatiza la publicacion de artefactos multi-OS en GitHub Releases
- README incluye guia de instalacion especifica por plataforma

### Performance y Throughput
- Kafka Producer asincrono con batching nativo
- GenerationEngine usa virtual threads (Java 21) para paralelismo
- Serializacion en batch cuando es posible
- Configuracion de linger.ms y batch.size expuesta al usuario

### IA como Enhancement (no dependencia)
- El sistema funciona completamente sin IA
- IA solo se usa para: proponer esquemas de dominios custom, generar datos mas realistas, refinar esquemas
- Fallback automatico a DataFaker cuando IA no esta disponible
- IA nunca bloquea el flujo principal

---

## Progreso de Implementacion

| Fase | Descripcion | Estado |
|------|-------------|--------|
| Git  | Setup repo, ramas master/develop, remoto | Completado |
| 1    | Fundacion y estructura del proyecto | Completado |
| 2    | Modelo de dominios y esquemas (healthcare + ecommerce) | En progreso |
| 3    | Motor de generacion y planificador | Pendiente |
| 4    | Integracion con Kafka Producer | Pendiente |
| 5    | Serializadores multi-formato | Pendiente |
| 6    | Dominios adicionales (energy, autos, highway) | Pendiente |
| 7    | Integracion con IA (LLM) | Pendiente |
| 8    | CLI interactivo completo | Pendiente |
| 9    | Sistema de configuracion y perfiles | Pendiente |
| 10   | Docker, distribucion y documentacion | Pendiente |

---

## Roadmap Futuro (Post-MVP)

1. **Schema Registry Integration**: Registro de esquemas en Confluent Schema Registry
2. **Web UI**: Dashboard web ademas del CLI
3. **Referencia de Datos (Data Replay)**: Replay de eventos reales grabados
4. **Azure Event Hubs**: Soporte para Azure Event Hubs via compatibilidad Kafka
5. **Google Cloud Pub/Sub**: Puente a GCP Pub/Sub
6. **Metrics Export**: Prometheus/Grafana para observabilidad
7. **Domain Marketplace**: Comunidad comparte dominios como paquetes
8. **Kafka Streams Pipeline**: Generar tambien consumers y procesadores
9. **Connector Framework**: Integracion con JDBC, Debezium, etc.
10. **Trafico Realista**: Patrones de trafico basados en horas del dia, eventos especiales
11. **Validacion de Contratos**: Verificar que los eventos cumplen un contrato/AVDL
12. **GraphQL Query Mode**: Permite generar eventos desde consultas GraphQL interactivas
