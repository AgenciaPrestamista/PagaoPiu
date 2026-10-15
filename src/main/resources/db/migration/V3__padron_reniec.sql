INSERT INTO reniec_padron (dni, nombre_completo, nacionalidad, dni_vigente, fallecido) VALUES
-- Caso de la API externa
('27427864', 'CASTILLO TERRONES, JOSE PEDRO', 'PERUANA', TRUE, FALSE),

-- Casos peruanos válidos con nombres con tildes, eñes y diacríticos
('10000001', 'Juan Carlos Pérez López', 'PERUANA', TRUE, FALSE),
('10000002', 'María Elena García Ríos', 'PERUANA', TRUE, FALSE),
('10000003', 'José Antonio Núñez Ávila', 'PERUANA', TRUE, FALSE),
('10000004', 'Ana Belén Gómez Díaz', 'PERUANA', TRUE, FALSE),
('10000005', 'Víctor Raúl Haya De La Torre', 'PERUANA', TRUE, FALSE),
('10000006', 'César Abraham Vallejo Mendoza', 'PERUANA', TRUE, FALSE),
('10000007', 'Lucía Fernanda Sánchez Chávez', 'PERUANA', TRUE, FALSE),
('10000008', 'Ángel Gabriel Peña Morales', 'PERUANA', TRUE, FALSE),
('10000009', 'Rosa María Flores Benítez', 'PERUANA', TRUE, FALSE),
('10000010', 'Óscar Iván Gutiérrez Quiroz', 'PERUANA', TRUE, FALSE),
('10000011', 'Inés Del Carmen Ortiz Ramos', 'PERUANA', TRUE, FALSE),
('10000012', 'Héctor Manuel Aguirre Mendoza', 'PERUANA', TRUE, FALSE),
('10000013', 'Sofía Alejandra Paz Soldán', 'PERUANA', TRUE, FALSE),
('10000014', 'Matías Alonso Vera Cárdenas', 'PERUANA', TRUE, FALSE),
('10000015', 'Clara Isabel Castañeda Ruíz', 'PERUANA', TRUE, FALSE),
('10000016', 'Raúl Ernesto Cáceres Polo', 'PERUANA', TRUE, FALSE),
('10000017', 'Valeria Nicole Campos Guzmán', 'PERUANA', TRUE, FALSE),

-- Casos especiales para validación de R2
('99999901', 'Carlos Muerto Ejemplo', 'PERUANA', TRUE, TRUE),      -- Fallecido (fallecido=true)
('99999902', 'Ana DNI Vencido Ejemplo', 'PERUANA', FALSE, FALSE),   -- DNI no vigente (dni_vigente=false)
('99999903', 'John Smith Foreign', 'ESTADOUNIDENSE', TRUE, FALSE);  -- Extranjero (nacionalidad != PERUANA)
