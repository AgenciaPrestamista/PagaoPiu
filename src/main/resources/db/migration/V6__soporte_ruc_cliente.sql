-- ── Permitir almacenar tanto DNI (8 caracteres) como RUC (11 caracteres) ──
ALTER TABLE cliente ALTER COLUMN dni TYPE VARCHAR(20);
