CREATE TABLE sucursal (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nombre VARCHAR(200) NOT NULL,
    rol VARCHAR(20) NOT NULL CHECK (rol IN ('ASESOR', 'GERENTE')),
    sucursal_id BIGINT REFERENCES sucursal(id),
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE reniec_padron (
    dni CHAR(8) PRIMARY KEY,
    nombre_completo VARCHAR(300) NOT NULL,
    nacionalidad VARCHAR(50) NOT NULL,
    dni_vigente BOOLEAN NOT NULL,
    fallecido BOOLEAN NOT NULL
);

CREATE TABLE cliente (
    id BIGSERIAL PRIMARY KEY,
    dni CHAR(8) NOT NULL UNIQUE,
    nombre_completo VARCHAR(300) NOT NULL,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('ACTIVO', 'MOROSO', 'EXTRAJUDICIAL'))
);

CREATE TABLE prestamo (
    id BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT NOT NULL REFERENCES cliente(id),
    sucursal_id BIGINT NOT NULL REFERENCES sucursal(id),
    asesor_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha_prestamo DATE NOT NULL,
    capital NUMERIC(12, 2) NOT NULL CHECK (capital > 0 AND capital <= 10000),
    plazo_meses INTEGER NOT NULL CHECK (plazo_meses >= 1 AND plazo_meses <= 60),
    tea NUMERIC(15, 10) NOT NULL,
    tem NUMERIC(15, 10) NOT NULL,
    cuota_fija NUMERIC(12, 2) NOT NULL,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('VIGENTE', 'MOROSO', 'EXTRAJUDICIAL', 'CANCELADO')),
    fecha_cancelacion DATE
);

CREATE TABLE pago (
    id BIGSERIAL PRIMARY KEY,
    prestamo_id BIGINT NOT NULL REFERENCES prestamo(id),
    sucursal_id BIGINT NOT NULL REFERENCES sucursal(id),
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha_pago DATE NOT NULL,
    monto NUMERIC(12, 2) NOT NULL,
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('CUOTA', 'CANCELACION_ANTICIPADA'))
);

CREATE TABLE cuota (
    id BIGSERIAL PRIMARY KEY,
    prestamo_id BIGINT NOT NULL REFERENCES prestamo(id),
    numero INTEGER NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    capital NUMERIC(12, 2) NOT NULL,
    interes NUMERIC(12, 2) NOT NULL,
    monto NUMERIC(12, 2) NOT NULL,
    saldo_capital NUMERIC(12, 2) NOT NULL,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('PENDIENTE', 'PAGADA')),
    pago_id BIGINT REFERENCES pago(id),
    CONSTRAINT uq_prestamo_numero UNIQUE (prestamo_id, numero)
);
