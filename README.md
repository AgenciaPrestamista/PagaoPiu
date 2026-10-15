# 🏦 PagaOPiu — Sistema Interno de Préstamos Personales

> **Proyecto de Evaluación — Scrum Sprint 1 (MVP)**  
> **Fecha de Evaluación:** Lunes 19 de octubre de 2026  
> **Stack:** Java 21 LTS | Spring Boot 4.1.1 | PostgreSQL 18.6 | React 19 + Vite | Bootstrap 5  

---

## 📋 Descripción General

**PagaOPiu** es un sistema web empresarial para la gestión, desembolso, amortización y cobranza de préstamos personales de consumo, distribuido en una red de 5 sucursales en el norte del Perú (Chimbote, Trujillo, Chiclayo, Piura y Tumbes).

El sistema cuenta con una arquitectura desacoplada:
- **Backend:** API REST con Spring Boot, Spring Security (sesiones HTTP seguras basadas en cookies y protección CORS), Spring Data JPA con bloqueo pesimista concurrente, Flyway para migraciones y cálculo financiero de alta precisión con `BigDecimal`.
- **Frontend:** Single Page Application (SPA) en React + Vite + Bootstrap 5, con componentes interactivos para búsqueda de identidad, simulación de cuotas, registro de préstamos, detalle de cronogramas con amortizaciones y panel ejecutivo para la gerencia general.

---

## 🚀 Puesta en Marcha

### Prerrequisitos
- **Java 21 LTS**
- **Node.js 18+** y **npm**
- **PostgreSQL 18.6** corriendo en el puerto `5432` con la base de datos `pagaopiudb` (usuario `postgres`, contraseña `blae`).

### 1. Iniciar el Backend (Spring Boot)
```powershell
./mvnw.cmd spring-boot:run
```
El servidor backend iniciará en `http://localhost:8080`.
Al arrancar por primera vez, Flyway ejecuta automáticamente las migraciones y el inicializador `DemoDataInitializer` siembra los 4 escenarios de prueba para la evaluación.

### 2. Iniciar el Frontend (React + Vite)
```powershell
cd ../pegapiu-frontend
npm install
npm run dev
```
La aplicación web estará disponible en `http://localhost:5173`.

---

## 👥 Usuarios de Prueba y Credenciales

Todos los usuarios cuentan con la contraseña: **`password123`**

| Rol | Usuario | Nombre | Sucursal Asignada | Alcance Operativo |
|---|---|---|---|---|
| **GERENTE** | `gerente` | Gerente General | Global | Acceso exclusivo al Panel de Control y Supervisión (R14). Solo lectura. |
| **ASESOR** | `asesor.trujillo` | Asesor Trujillo | Trujillo | Registro de préstamos en Trujillo, búsqueda de identidad RENIEC, cobro de cuotas. |
| **ASESOR** | `asesor.piura` | Asesor Piura | Piura | Operaciones en Piura y cobro multicanal de clientes de cualquier sucursal. |
| **ASESOR** | `asesor.chiclayo` | Asesor Chiclayo | Chiclayo | Operaciones en Chiclayo y cobro multicanal. |
| **ASESOR** | `asesor.chimbote` | Asesor Chimbote | Chimbote | Operaciones en Chimbote y cobro multicanal. |
| **ASESOR** | `asesor.tumbes` | Asesor Tumbes | Tumbes | Operaciones en Tumbes y cobro multicanal. |

---

## 🎯 Datos Demo para Evaluación en Vivo

La base de datos se inicializa automáticamente con los 4 escenarios representativos:

| Escenario | DNI | Nombre Completo | Sucursal Origen | Estado Préstamo | Estado Cliente | Observaciones |
|---|---|---|---|---|---|---|
| **1. Préstamo al día** | `27427864` | CASTILLO TERRONES, JOSE PEDRO | Trujillo | `VIGENTE` | `ACTIVO` | Cuotas 1 y 2 pagadas puntualmente. Restante al día. |
| **2. Cliente moroso** | `10000001` | Juan Carlos Pérez López | Piura | `MOROSO` | `MOROSO` | Cuota 1 vencida hace 15 días ($< \text{hoy}$). Bloqueado para nuevos créditos. |
| **3. Extrajudicial** | `10000002` | María Elena García Ríos | Chiclayo | `EXTRAJUDICIAL` | `EXTRAJUDICIAL` | Atraso $\ge 365$ días. No admite pagos ordinarios ni revierte solo. |
| **4. Al tope de deuda** | `10000006` | César Abraham Vallejo Mendoza | Chimbote | `VIGENTE` | `ACTIVO` | Saldo pendiente = S/ 10,000.00. Nueva solicitud es rechazada por exceder cupo. |

---

## 📜 Reglas de Negocio Implementadas (R1 a R14)

### Registro de Préstamo
- **R1 (Campos Obligatorios):** DNI, nombre del cliente, monto, plazo y fecha del crédito son obligatorios tanto en DTO como en servicio transaccional.
- **R2 (Validación RENIEC):** El DNI debe existir en el padrón nacional, tener estado vigente (`dni_vigente = true`), no figurar como fallecido y pertenecer a ciudadano peruano.
- **R3 (Nombre Exacto con NFC):** El nombre ingresado debe coincidir de forma estricta (sensible a mayúsculas, minúsculas y tildes diacríticas) aplicando normalización Unicode NFC en ambos extremos.
- **R4 (Rango de Plazo):** El plazo debe encontrarse entre 1 y 60 meses calendario.
- **R5 (Rango de Monto):** Monto mínimo configurable (S/ 1.00) y monto máximo de S/ 10,000.00.
- **R6 (Tope de Deuda de S/ 10,000.00):** La suma del capital de las cuotas pendientes de préstamos activos más el nuevo crédito no puede superar los S/ 10,000.00. Se asegura la consistencia con bloqueo pesimista `PESSIMISTIC_WRITE` a nivel de fila de base de datos.
- **R7 (Filtro de Morosidad):** Clientes con estado `MOROSO` o `EXTRAJUDICIAL` son rechazados automáticamente tras recálculo en tiempo de solicitud.
- **R8 (Fecha no Futura):** La fecha del préstamo debe ser igual o anterior a la fecha actual del sistema (`Clock`), rechazando fechas futuras.
- **R9 (Cronograma Francés):** Amortización por cuota fija (método francés) con TEA del 30%, TEM calculada a 10 decimales, vencimientos cada 30 días, redondeo `HALF_UP` y absorción del céntimo residual en la última cuota para saldo final exacto de S/ 0.00.

### Cobranza y Pagos
- **R10 (Pago Secuencial):** Cobro exclusivo de la cuota pendiente más antigua. No admite amortizaciones parciales ni salteo de cuotas. Bloqueo pesimista `PESSIMISTIC_WRITE` para evitar carreras concurrentes.
- **R11 (Cobro Multicanal Intersucursales):** Los clientes pueden pagar en cualquiera de las 5 sucursales. El recaudo se registra y contabiliza para la sucursal del asesor que ejecuta el cobro.
- **R12 (Cancelación Anticipada):** Liquidación inmediata de todas las cuotas pendientes en una sola transacción, marcando el crédito como `CANCELADO` con fecha de cancelación sin recálculo de intereses.

### Estados y Supervisión Gerencial
- **R13 (Estados Dinámicos y Proceso Nocturno):** 
  - Si una cuota pendiente vence ($< \text{hoy}$), el préstamo pasa a `MOROSO` y el cliente a `MOROSO`.
  - Si el atraso de la cuota más antigua alcanza $\ge 365$ días, pasa a `EXTRAJUDICIAL` (no admite pagos del sistema y no revierte solo).
  - Al saldar las cuotas morosas, el préstamo y cliente revierten automáticamente a `VIGENTE` y `ACTIVO`.
  - Tarea programada `@Scheduled(cron = "0 0 1 * * *")` 100% idempotente que reclasifica la cartera todas las madrugadas.
- **R14 (Panel del Gerente General):** 
  - El Asesor tiene permisos de registro y cobro pero recibe `403 Forbidden` en el panel gerencial.
  - El Gerente General cuenta con un dashboard ejecutivo exclusivo de solo lectura que presenta:
    - Préstamos activos clasificados por **sucursal de origen**.
    - Recaudación e intereses devengados clasificados por **sucursal de pago**.
    - Conteo global de clientes en mora y cobranza judicial.

---

## 🧪 Ejecución de Pruebas Automatizadas

El proyecto cuenta con **57 pruebas de integración** contra PostgreSQL real (sin mocks de repositorios ni bases en memoria H2):

```powershell
# Ejecutar la suite completa (57 tests)
./mvnw.cmd test

# Ejecutar la suite trazable de reglas de negocio R1 a R14
./mvnw.cmd test -Dtest=ReglasNegocioTrazablesIntegrationTest

# Ejecutar pruebas por módulo específico
./mvnw.cmd test -Dtest=PrestamoIntegrationTest
./mvnw.cmd test -Dtest=PagoIntegrationTest
./mvnw.cmd test -Dtest=EstadoIntegrationTest
./mvnw.cmd test -Dtest=PanelIntegrationTest
```
