# 🏦 PagaOPiu — Backend

API REST del sistema interno de préstamos personales **PagaOPiu**: registro y formalización de créditos, contratos, cobranza, control de caja por agencia y supervisión gerencial.

> **Proyecto de Evaluación — Scrum Sprint 1 (MVP)**
> **Stack:** Java 21 · Spring Boot 4.1.1 · Spring Security · Spring Data JPA · Flyway · PostgreSQL 18.6
> **Frontend:** [`../pegapiu-frontend`](../pegapiu-frontend/README.md) (React 19 + Vite)

## Contenido

1. [Descripción general](#-descripción-general)
2. [Puesta en marcha](#-puesta-en-marcha)
3. [Variables de entorno](#-variables-de-entorno)
4. [Usuarios de prueba](#-usuarios-de-prueba)
5. [Datos demo](#-datos-demo)
6. [Módulos y reglas de negocio](#-módulos-y-reglas-de-negocio)
7. [API REST](#-api-rest)
8. [Base de datos y migraciones](#-base-de-datos-y-migraciones)
9. [Pruebas automatizadas](#-pruebas-automatizadas)
10. [Docker y despliegue](#-docker-y-despliegue)

---

## 📋 Descripción general

**PagaOPiu** gestiona el ciclo completo de un préstamo en una red de 5 sucursales del norte del Perú (Chimbote, Trujillo, Chiclayo, Piura y Tumbes):

- **Originación:** validación de identidad con RENIEC (personas) y SUNAT (empresas con RUC), simulación del cronograma y generación del contrato.
- **Formalización:** impresión del contrato, carga del contrato firmado (con hash SHA-256) y activación del préstamo.
- **Cobranza:** cobro secuencial de cuotas, cancelación anticipada y comprobantes electrónicos simulados (boleta y factura).
- **Caja por agencia:** apertura con la remesa de Hermes, incidencias por diferencias, descuento de desembolsos y cierre diario.
- **Supervisión:** panel ejecutivo del gerente, administración de personal y consulta de cierres de caja.

Arquitectura desacoplada: este servicio expone la API y el frontend la consume con sesión HTTP por cookie (CORS con credenciales). Los cálculos financieros usan `BigDecimal` y los cobros usan bloqueo pesimista para evitar carreras concurrentes.

---

## 🚀 Puesta en marcha

### Prerrequisitos
- **Java 21 LTS**
- **PostgreSQL 18.x** en el puerto `5432` con una base `pagaopiudb`
- **Node.js 20+** y **npm** (para el frontend)

### 1. Iniciar el backend

`application.properties` **no define valores por defecto** para la base de datos ni para el origen del frontend, así que hay que indicarlos por variables de entorno:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/pagaopiudb"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<su-contraseña>"
$env:FRONTEND_URL = "http://localhost:5173"
$env:RENIEC_PROVIDER = "simulado"     # sin token de API externa (ver más abajo)

./mvnw.cmd spring-boot:run
```

El servidor queda en `http://localhost:8080`. Al arrancar, Flyway aplica las migraciones pendientes.

### 2. Iniciar el frontend

```powershell
cd ../pegapiu-frontend
npm install
npm run dev
```

La aplicación web queda en `http://localhost:5173` y reenvía `/api` al backend (proxy de Vite).

---

## ⚙️ Variables de entorno

<!-- AUTO-GENERATED: variables de entorno (fuente: src/main/resources/application.properties y @Value en el código) -->

| Variable | Obligatoria | Por defecto | Descripción |
|---|---|---|---|
| `DB_URL` | Sí | — | URL JDBC de PostgreSQL, p. ej. `jdbc:postgresql://localhost:5432/pagaopiudb` |
| `DB_USERNAME` | Sí | — | Usuario de la base de datos |
| `DB_PASSWORD` | Sí | — | Contraseña de la base de datos |
| `FRONTEND_URL` | Sí | — | Origen(es) permitidos por CORS, separados por coma (p. ej. `http://localhost:5173`) |
| `PORT` | No | `8080` | Puerto del servidor |
| `RENIEC_PROVIDER` | No | `api` | `api` usa api.json.pe; `simulado` usa el padrón local (`reniec_padron`) |
| `RENIEC_API_URL` | No | `https://api.json.pe/api/dni` | Endpoint de consulta de DNI |
| `RENIEC_API_TOKEN` | No | vacío | Token de api.json.pe (también se usa para SUNAT) |
| `STORAGE_PROVIDER` | No | `local` | Almacenamiento de contratos firmados |
| `STORAGE_DIR` | No | `./uploads/contratos` | Carpeta de contratos cuando el almacenamiento es local |
| `FIRMA_GERENTE_PATH` | No | vacío | Ruta de la imagen de firma del gerente para el contrato |
| `CLOUDINARY_CLOUD_NAME` / `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` | No | vacío | Credenciales de Cloudinary |
| `APP_DEMO_SEED_ENABLED` | No | `true` | Siembra datos demo si la base no tiene préstamos |
| `APP_CRON_RECALCULO_ESTADOS` | No | `0 0 1 * * *` | Cron del recálculo de morosidad (zona America/Lima) |
| `APP_CRON_VENCIMIENTO_CONTRATOS` | No | `0 5 0 * * *` | Cron del vencimiento de contratos en espera (zona America/Lima) |

Parámetros de negocio fijos en `application.properties`: `app.prestamo.monto-minimo=1`, `app.prestamo.monto-maximo=10000`, `app.prestamo.tea=0.30`, datos del emisor del contrato (`app.emisor.*`) y límite de carga de archivos de 5 MB. Las cookies de sesión se emiten con `SameSite=None; Secure`.

<!-- /AUTO-GENERATED -->

---

## 👥 Usuarios de prueba

Todos los usuarios demo (migración `V2.1`) tienen la contraseña: **`password123`**

| Rol | Usuario | Sucursal | Alcance operativo |
|---|---|---|---|
| **GERENTE** | `gerente` | Global | Panel de supervisión, administración de personal, cierre de incidencias de Hermes y consulta de cierres de caja. |
| **ASESOR** | `asesor.chimbote` | Chimbote | Opera su agencia: apertura y cierre de caja, préstamos, cobranza. |
| **ASESOR** | `asesor.trujillo` | Trujillo | Ídem. |
| **ASESOR** | `asesor.chiclayo` | Chiclayo | Ídem. |
| **ASESOR** | `asesor.piura` | Piura | Ídem. |
| **ASESOR** | `asesor.tumbes` | Tumbes | Ídem. |

Los asesores pueden cobrar préstamos de cualquier sucursal (cobro intersucursal); el recaudo se contabiliza para la agencia que cobra.

---

## 🎯 Datos demo

Con `APP_DEMO_SEED_ENABLED=true` (valor por defecto), `DemoDataInitializer` siembra estos escenarios **solo si la tabla `prestamo` está vacía**:

| Escenario | DNI | Nombre | Agencia | Préstamo | Cliente |
|---|---|---|---|---|---|
| Préstamo al día | `27427864` | CASTILLO TERRONES, JOSE PEDRO | Trujillo | `VIGENTE` | `ACTIVO` |
| Cliente moroso | `10000001` | Juan Carlos Pérez López | Piura | `MOROSO` | `MOROSO` |
| Extrajudicial | `10000002` | María Elena García Ríos | Chiclayo | `EXTRAJUDICIAL` | `EXTRAJUDICIAL` |
| Al tope de deuda | `10000006` | César Abraham Vallejo Mendoza | Chimbote | `VIGENTE` | `ACTIVO` |

Padrón RENIEC de prueba (`V3`): DNI `10000001` a `10000017` (peruanos válidos), `99999901` (fallecido), `99999902` (DNI no vigente) y `99999903` (extranjero).
Empresas de prueba (modo simulado): RUC `20987654321` (Comercial Los Andes, representante DNI `10000001`), `20123456789` (representante `10000002`) y `20552103816`; cualquier otro RUC de 11 dígitos devuelve una empresa genérica.

---

## 📜 Módulos y reglas de negocio

### Registro de préstamo (R1 a R9)
- **R1 (Campos obligatorios):** DNI, nombre, monto, plazo y fecha del crédito.
- **R2 (Validación RENIEC):** el DNI debe existir, estar vigente, no figurar como fallecido y ser de ciudadano peruano.
- **R3 (Nombre exacto con NFC):** el nombre debe coincidir con RENIEC, con normalización Unicode NFC y sensible a tildes.
- **R4 (Plazo):** entre 1 y 60 meses.
- **R5 (Monto):** mínimo configurable (S/ 1.00) y máximo de S/ 10,000.00.
- **R6 (Tope de deuda):** el saldo pendiente de préstamos activos más el nuevo crédito no puede superar S/ 10,000.00 (bloqueo `PESSIMISTIC_WRITE`).
- **R7 (Filtro de morosidad):** los clientes `MOROSO` o `EXTRAJUDICIAL` son rechazados.
- **R8 (Fecha):** la fecha del préstamo debe ser la fecha actual del sistema (zona America/Lima).
- **R9 (Cronograma francés):** cuota fija, TEA 30 %, TEM a 10 decimales, vencimientos cada 30 días, redondeo `HALF_UP` y céntimo residual absorbido en la última cuota.

### Contratos y empresas (RUC)
- El contrato nace en `EN_ESPERA`, se imprime y se activa (`VIGENTE`) al subir el contrato firmado con el mismo número (se guarda su hash SHA-256 y se rechazan archivos repetidos). Un contrato no firmado vence con la tarea `vencimiento-contratos`.
- **RUC 20 (empresas):** la empresa debe estar `ACTIVO` y `HABIDO` en SUNAT. El asesor ingresa el **DNI de la persona que se presenta** y el servidor comprueba que sea **uno de los representantes legales** listados por SUNAT, que su DNI esté vigente en RENIEC y que el nombre coincida. Los datos del representante los toma el servidor de SUNAT.
- **Regla M2:** `GET /api/cliente/buscar` **no devuelve** nombre ni DNI del representante (solo su cargo); `GET /api/cliente/validar-representante` responde únicamente `valido` y un mensaje.
- **RUC 10:** el DNI presentado debe coincidir con los dígitos 3 a 10 del RUC.

### Cobranza y pagos (R10 a R12)
- **R10 (Pago secuencial):** solo se cobra la cuota pendiente más antigua, sin pagos parciales ni salto de cuotas.
- **R11 (Cobro intersucursal):** se puede pagar en cualquier sucursal; el recaudo cuenta para la sucursal del asesor.
- **R12 (Cancelación anticipada):** liquida todas las cuotas pendientes en una transacción y marca el crédito como `CANCELADO`.

### Estados y morosidad (R13)
- Una cuota pendiente con vencimiento anterior a hoy vuelve el préstamo y el cliente `MOROSO`.
- Con atraso de la cuota más antigua de **365 días o más** pasa a `EXTRAJUDICIAL`: no admite pagos en ventanilla y no revierte solo.
- Al saldar las cuotas vencidas, el préstamo vuelve a `VIGENTE` y el cliente a `ACTIVO`.
- El recálculo ocurre **al registrar un pago** y con la tarea programada nocturna (01:00, America/Lima). Entre un vencimiento y la siguiente ejecución el préstamo sigue como `VIGENTE`.
- La cuota vencida se cobra por su monto normal: el sistema **no calcula recargo por mora**.

### Caja por agencia y Hermes
- **Apertura:** el asesor registra la remesa diaria de Hermes (código de un solo uso, monto apto y monto observado; el esperado es S/ 50,000.00). Solo el monto apto ingresa al saldo.
- **Incidencias:** si hay monto observado o diferencia, se exige un motivo y se crea una incidencia `ABIERTA` que solo el gerente puede cerrar con una resolución escrita.
- **Desembolso:** al activar un préstamo se descuenta su capital del saldo de la caja de la agencia (falla si el saldo es insuficiente o la caja ya fue cerrada).
- **Cierre diario:** `POST /api/caja/cerrar` registra saldo final, total cobrado del día, quién cerró y una observación. Después del cierre no se aceptan remesas ni desembolsos. El gerente consulta los cierres en `GET /api/caja/cierres`.
- Los cobros de cuotas suman al "total cobrado" del día, pero **no incrementan el saldo disponible** de la caja.

### Supervisión gerencial (R14)
- El panel muestra préstamos activos por **sucursal de origen**, recaudación e intereses por **sucursal de pago**, y el conteo de clientes en mora y en cobranza judicial.
- El rol `ASESOR` recibe `403` en los recursos del gerente.

---

## 🔌 API REST

<!-- AUTO-GENERATED: endpoints (fuente: controller/ y security/SecurityConfig.java) -->

Todas las rutas cuelgan de `/api` y usan sesión por cookie. Un `401` significa que no hay sesión; un `403`, que el rol no tiene permiso.

| Recurso | Endpoints | Acceso |
|---|---|---|
| `/api/auth` | `POST /login` · `GET /me` · `POST /logout` · `POST /cambiar-password` | Público (login/me); sesión (resto) |
| `/api/cliente` | `GET /buscar` · `GET /validar-representante` | `ASESOR` |
| `/api/prestamo` | `GET /{id}` · `GET /cliente/{dni}` · `GET /simular` · `GET /recientes` | `ASESOR` (`/recientes`: asesor o gerente) |
| `/api/contratos` | `POST /generar` · `GET /{id}` · `POST /{id}/imprimir` · `POST /{id}/cancelar` · `POST /{id}/subir-firmado` · `GET /{id}/descargar` · `GET /en-espera` · `GET /firma-gerente` | `ASESOR`, `GERENTE` |
| `/api/pago` | `POST /cuota/{prestamoId}` · `POST /cancelar/{prestamoId}` · `GET /prestamo/{prestamoId}` · `GET /{id}` | `ASESOR` |
| `/api/caja` | `POST /hermes/recepcion` · `POST /cerrar` · `GET /estado` · `GET /cierres` | Sesión (`/cierres`: `GERENTE`) |
| `/api/incidencias` | `GET /` · `POST /{id}/cerrar` | Sesión (cerrar: `GERENTE`, validado en el servicio) |
| `/api/sucursales` | `GET /` | `ASESOR`, `GERENTE` |
| `/api/usuarios` | `GET /` · `POST /` · `PUT /{id}/activo` · `PUT /{id}/password` · `GET /sucursales` | `GERENTE` |
| `/api/panel` | `GET /` | `GERENTE` |

<!-- /AUTO-GENERATED -->

---

## 🗄️ Base de datos y migraciones

Flyway (`classpath:db/migration`) administra el esquema y Hibernate solo lo valida (`ddl-auto=validate`).

<!-- AUTO-GENERATED: migraciones (fuente: src/main/resources/db/migration) -->

| Migración | Contenido |
|---|---|
| `V1__esquema` | Esquema base: clientes, préstamos, cuotas, pagos y usuarios |
| `V2__sucursales` / `V2.1__usuarios_demo` | Las 5 sucursales y los usuarios demo |
| `V3__padron_reniec` | Padrón RENIEC de prueba |
| `V4__contrato_y_estados` | Contratos y nuevos estados de préstamo |
| `V5__comprobantes_pago` | Comprobantes de pago (boleta y factura) |
| `V6__soporte_ruc_cliente` | Soporte de RUC para clientes empresa |
| `V7__snapshot_empresa` | Datos de la empresa y su representante en el contrato |
| `V8__caja_hermes_incidencias` | Caja por sucursal, remesas de Hermes e incidencias |
| `V9__cierre_caja` | Columnas de cierre diario de caja |

<!-- /AUTO-GENERATED -->

---

## 🧪 Pruebas automatizadas

Hay **128 pruebas** en 19 clases: integración contra PostgreSQL real (sin H2 ni mocks de repositorios) y pruebas unitarias del cálculo del cronograma.

> ⚠️ **Las pruebas usan por defecto la base `pagaopiudb` y su limpiador (`TestDatabaseCleaner`) ejecuta `DELETE` sobre `incidencia_hermes`, `entrega_hermes`, `caja_sucursal`, `cuota`, `pago`, `prestamo` y `cliente`.**
> Ejecútelas siempre contra una base **separada** para no perder datos:

```powershell
# Crear una vez la base de pruebas (Flyway aplica las migraciones al primer uso)
createdb -U postgres pagaopiudb_test

$env:DB_URL = "jdbc:postgresql://localhost:5432/pagaopiudb_test"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<su-contraseña>"

./mvnw.cmd test                                                  # suite completa
./mvnw.cmd test -Dtest=ReglasNegocioTrazablesIntegrationTest     # reglas R1 a R14
./mvnw.cmd test -Dtest=HermesCajaIntegrationTest                 # caja y remesas
./mvnw.cmd test -Dtest=ValidarRepresentanteIntegrationTest       # representante de empresa
./mvnw.cmd test -Dtest=PrestamoIntegrationTest
./mvnw.cmd test -Dtest=PagoIntegrationTest
./mvnw.cmd test -Dtest=EstadoIntegrationTest
./mvnw.cmd test -Dtest=PanelIntegrationTest
```

---

## 🐳 Docker y despliegue

- **Imagen:** `Dockerfile` en dos etapas (Maven + JDK 21 para compilar; JRE 21 Alpine para ejecutar, con usuario sin privilegios). Expone el puerto `8080` y guarda los contratos en `/app/uploads/contratos`.
- **CI/CD:** `.github/workflows/ci-cd.yml` ejecuta `./mvnw clean test` y el empaquetado en cada push o pull request a `main`/`master`, y despliega la imagen a **Google Cloud Run** (Artifact Registry) con el secreto `GCP_SA_KEY`.
- **En producción** deben definirse `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `FRONTEND_URL` (la URL pública del frontend); el resto según la tabla de variables. Las cookies `Secure` exigen HTTPS entre frontend y backend.
