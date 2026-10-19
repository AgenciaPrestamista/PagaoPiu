-- ── Modificación de estados del préstamo ──
ALTER TABLE prestamo DROP CONSTRAINT IF EXISTS prestamo_estado_check;
ALTER TABLE prestamo ADD CONSTRAINT prestamo_estado_check
    CHECK (estado IN ('VIGENTE', 'MOROSO', 'EXTRAJUDICIAL', 'CANCELADO', 'EN_ESPERA', 'ANULADO'));

-- ── Nuevos campos para el flujo de contrato ──
ALTER TABLE prestamo
    ADD COLUMN numero_contrato VARCHAR(30) UNIQUE,
    ADD COLUMN impreso BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN fecha_impresion TIMESTAMP,
    ADD COLUMN motivo_anulacion VARCHAR(500),
    ADD COLUMN contrato_nombre_original VARCHAR(255),
    ADD COLUMN contrato_ruta VARCHAR(500),
    ADD COLUMN contrato_hash VARCHAR(64),
    ADD COLUMN contrato_content_type VARCHAR(100),
    ADD COLUMN contrato_subido_por BIGINT REFERENCES usuario(id),
    ADD COLUMN contrato_fecha_subida TIMESTAMP;

-- ── Restricción: motivo de anulación obligatorio si el estado es ANULADO ──
ALTER TABLE prestamo ADD CONSTRAINT prestamo_motivo_anulacion_check
    CHECK (estado <> 'ANULADO' OR motivo_anulacion IS NOT NULL);

-- ── Índice único para evitar reutilización del mismo archivo (hash SHA-256) ──
CREATE UNIQUE INDEX uq_prestamo_contrato_hash ON prestamo(contrato_hash) WHERE contrato_hash IS NOT NULL;

-- ── Secuencia correlativa para número de contrato (CTR-YYYY-NNNNNN) ──
CREATE SEQUENCE seq_numero_contrato START WITH 1 INCREMENT BY 1;
