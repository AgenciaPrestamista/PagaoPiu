-- ── Secuencias para correlativo único de comprobantes (F001-XXXXXX y B001-XXXXXX) ──
CREATE SEQUENCE IF NOT EXISTS seq_numero_boleta START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_numero_factura START WITH 1 INCREMENT BY 1;

-- ── Nuevas columnas en tabla pago para comprobantes de pago ──
ALTER TABLE pago
    ADD COLUMN IF NOT EXISTS numero_comprobante VARCHAR(30) UNIQUE,
    ADD COLUMN IF NOT EXISTS tipo_comprobante VARCHAR(20) DEFAULT 'BOLETA';

-- ── Asignar correlativo retrocompatible a pagos existentes ──
UPDATE pago
SET numero_comprobante = 'B001-' || LPAD(id::text, 6, '0'),
    tipo_comprobante = 'BOLETA'
WHERE numero_comprobante IS NULL;
