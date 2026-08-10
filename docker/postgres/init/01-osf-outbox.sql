-- Tabla outbox creada de antemano para que Debezium pueda arrancar antes de que el generador
-- escriba nada. El sink la crea igual si no existe (DdlExecutor), asi que esto es solo
-- conveniencia: las tablas de negocio se generan a partir del EventSchema de cada dominio.
--
-- Las cinco primeras columnas son las que espera por defecto el SMT
-- io.debezium.transforms.outbox.EventRouter.

CREATE TABLE IF NOT EXISTS osf_outbox (
    id            VARCHAR(64)  NOT NULL,
    aggregatetype VARCHAR(255) NOT NULL,   -- topic de destino resuelto por el TopicRouter
    aggregateid   VARCHAR(256) NOT NULL,   -- clave del mensaje
    type          VARCHAR(128) NOT NULL,   -- eventName o errorType
    payload       JSONB        NOT NULL,   -- el Event completo serializado
    seq           BIGSERIAL    NOT NULL,   -- orden total para el relay
    domain        VARCHAR(64)  NOT NULL,
    trace_id      VARCHAR(64),
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts      INT          NOT NULL DEFAULT 0,
    last_error    TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ,
    CONSTRAINT pk_osf_outbox PRIMARY KEY (id)
);

-- Indice parcial: se mantiene pequeno aunque la tabla acumule millones de filas
CREATE INDEX IF NOT EXISTS ix_osf_outbox_pending ON osf_outbox (seq) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS ix_osf_outbox_agg     ON osf_outbox (aggregateid, seq);
CREATE INDEX IF NOT EXISTS ix_osf_outbox_created ON osf_outbox (created_at);

-- El ciclo INSERT -> UPDATE -> DELETE del relay genera mucho bloat sin esto
ALTER TABLE osf_outbox SET (fillfactor = 70, autovacuum_vacuum_scale_factor = 0.02);

-- REPLICA IDENTITY FULL no hace falta: el conector solo rutea inserciones (skipped.operations).
