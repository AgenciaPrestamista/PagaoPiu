-- ── V9: Cierre diario de caja por sucursal ──

ALTER TABLE caja_sucursal
    ADD COLUMN cerrada BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN usuario_cierre_id BIGINT REFERENCES usuario(id),
    ADD COLUMN fecha_cierre TIMESTAMP,
    ADD COLUMN saldo_cierre NUMERIC(12, 2),
    ADD COLUMN total_cobrado_cierre NUMERIC(12, 2),
    ADD COLUMN observacion_cierre VARCHAR(500);
