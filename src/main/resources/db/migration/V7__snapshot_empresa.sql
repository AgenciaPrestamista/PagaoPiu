-- ── V7: Foto Histórica (Snapshot) de Empresa para Contrato Inmutable ──

ALTER TABLE prestamo
    ADD COLUMN IF NOT EXISTS empresa_razon_social VARCHAR(300),
    ADD COLUMN IF NOT EXISTS empresa_ruc VARCHAR(11),
    ADD COLUMN IF NOT EXISTS empresa_representante_nombre VARCHAR(300),
    ADD COLUMN IF NOT EXISTS empresa_representante_documento VARCHAR(20),
    ADD COLUMN IF NOT EXISTS empresa_representante_cargo VARCHAR(100);
