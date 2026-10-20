-- ── V8: Caja Sucursal, Recepción de Remesas Hermes e Incidencias ──

CREATE TABLE caja_sucursal (
    id BIGSERIAL PRIMARY KEY,
    sucursal_id BIGINT NOT NULL REFERENCES sucursal(id),
    fecha DATE NOT NULL,
    saldo_disponible NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    monto_observado_acumulado NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    abierta BOOLEAN NOT NULL DEFAULT FALSE,
    usuario_apertura_id BIGINT REFERENCES usuario(id),
    fecha_apertura TIMESTAMP,
    CONSTRAINT uq_caja_sucursal_fecha UNIQUE (sucursal_id, fecha)
);

CREATE TABLE entrega_hermes (
    id BIGSERIAL PRIMARY KEY,
    caja_id BIGINT REFERENCES caja_sucursal(id),
    sucursal_id BIGINT NOT NULL REFERENCES sucursal(id),
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha DATE NOT NULL,
    codigo_remesa VARCHAR(50) NOT NULL UNIQUE,
    monto_esperado NUMERIC(12, 2) NOT NULL DEFAULT 50000.00,
    monto_apto NUMERIC(12, 2) NOT NULL,
    monto_observado NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    motivo VARCHAR(500),
    estado VARCHAR(30) NOT NULL CHECK (estado IN ('RECIBIDA_CONFORME', 'RECIBIDA_CON_OBSERVACIONES')),
    fecha_recepcion TIMESTAMP NOT NULL
);

CREATE TABLE incidencia_hermes (
    id BIGSERIAL PRIMARY KEY,
    entrega_id BIGINT REFERENCES entrega_hermes(id),
    sucursal_id BIGINT NOT NULL REFERENCES sucursal(id),
    usuario_reporta_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha DATE NOT NULL,
    monto_esperado NUMERIC(12, 2) NOT NULL,
    monto_apto NUMERIC(12, 2) NOT NULL,
    monto_observado NUMERIC(12, 2) NOT NULL,
    diferencia NUMERIC(12, 2) NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('ABIERTA', 'CERRADA')),
    resolucion VARCHAR(500),
    usuario_cierre_id BIGINT REFERENCES usuario(id),
    fecha_cierre TIMESTAMP
);
