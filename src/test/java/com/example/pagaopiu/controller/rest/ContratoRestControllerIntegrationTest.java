package com.example.pagaopiu.controller.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.repository.PrestamoRepository;
import com.example.pagaopiu.service.PrestamoService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
public class ContratoRestControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private PrestamoRepository prestamoRepository;

    @Autowired
    private PrestamoService prestamoService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private Clock clock;

    private MockMvc mockMvc;
    private MockHttpSession sessionAsesorTrujillo;
    private MockHttpSession sessionAsesorChimbote;
    private MockHttpSession sessionGerente;

    private static final byte[] PDF_MAGIC_BYTES = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        // 1. Asesor Trujillo (sucursal 2)
        UserDetails asesorTrujillo = userDetailsService.loadUserByUsername("asesor.trujillo");
        Authentication authTrujillo = new UsernamePasswordAuthenticationToken(asesorTrujillo, "password123", asesorTrujillo.getAuthorities());
        SecurityContext contextTrujillo = SecurityContextHolder.createEmptyContext();
        contextTrujillo.setAuthentication(authTrujillo);
        sessionAsesorTrujillo = new MockHttpSession();
        sessionAsesorTrujillo.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contextTrujillo);

        // 2. Asesor Chimbote (sucursal 1)
        UserDetails asesorChimbote = userDetailsService.loadUserByUsername("asesor.chimbote");
        Authentication authChimbote = new UsernamePasswordAuthenticationToken(asesorChimbote, "password123", asesorChimbote.getAuthorities());
        SecurityContext contextChimbote = SecurityContextHolder.createEmptyContext();
        contextChimbote.setAuthentication(authChimbote);
        sessionAsesorChimbote = new MockHttpSession();
        sessionAsesorChimbote.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contextChimbote);

        // 3. Gerente General (sucursal global null)
        UserDetails gerenteDetails = userDetailsService.loadUserByUsername("gerente");
        Authentication authGerente = new UsernamePasswordAuthenticationToken(gerenteDetails, "password123", gerenteDetails.getAuthorities());
        SecurityContext contextGerente = SecurityContextHolder.createEmptyContext();
        contextGerente.setAuthentication(authGerente);
        sessionGerente = new MockHttpSession();
        sessionGerente.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contextGerente);
    }

    private String crearPrestamoJson(String dni, String nombre, BigDecimal monto, int plazo, LocalDate fecha) {
        return String.format(Locale.US,
                "{\"dni\":\"%s\",\"nombreCliente\":\"%s\",\"monto\":%.2f,\"plazoMeses\":%d,\"fechaPrestamo\":\"%s\"}",
                dni, nombre, monto.doubleValue(), plazo, fecha.toString());
    }

    @Test
    @DisplayName("Eliminación de rutas directas: POST /api/prestamo y POST /prestamo/registrar ya no existen")
    void testRutaCreacionDirectaEliminada() throws Exception {
        long conteoInicial = prestamoRepository.count();

        String json = crearPrestamoJson("10000007", "Lucía Fernanda Sánchez Chávez", new BigDecimal("1000.00"), 6, LocalDate.now(clock));

        // 1. POST /api/prestamo ya no tiene mapping registrado en la raíz
        mockMvc.perform(post("/api/prestamo")
                        .session(sessionAsesorTrujillo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(Matchers.in(List.of(404, 405))));

        // 2. POST /prestamo/registrar (MVC) con CSRF -> 405 Method Not Allowed (solo admite GET)
        mockMvc.perform(post("/prestamo/registrar")
                        .with(csrf())
                        .session(sessionAsesorTrujillo)
                        .param("dni", "10000007")
                        .param("nombreCliente", "Lucía Fernanda Sánchez Chávez")
                        .param("monto", "1000.00")
                        .param("plazoMeses", "6")
                        .param("fechaPrestamo", LocalDate.now(clock).toString()))
                .andExpect(status().isMethodNotAllowed());

        // Asegurar que no se creó ningún préstamo nuevo en base de datos
        long conteoFinal = prestamoRepository.count();
        assertThat(conteoFinal).isEqualTo(conteoInicial);
    }

    @Test
    @DisplayName("Seguridad: Endpoints /api/contratos/** devuelven 401 si no se está autenticado")
    void testRutasContrato_sinAutenticar_devuelve401() throws Exception {
        String json = crearPrestamoJson("10000007", "Lucía Fernanda Sánchez Chávez", new BigDecimal("1000.00"), 6, LocalDate.now(clock));
        MockMultipartFile file = new MockMultipartFile("archivo", "contrato.pdf", "application/pdf", PDF_MAGIC_BYTES);

        mockMvc.perform(post("/api/contratos/generar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/contratos/en-espera"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/contratos/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/contratos/1/imprimir"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/contratos/1/cancelar"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/api/contratos/1/subir-firmado")
                        .file(file)
                        .param("numeroContrato", "CTR-2026-000001"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/contratos/1/descargar"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Flujo completo: Generar -> Ver detalle -> Imprimir -> Reimprimir -> Subir firmado -> Descargar")
    void testFlujoCompletoContrato_generar_ver_imprimir_reimprimir_subir_descargar() throws Exception {
        String json = crearPrestamoJson("10000007", "Lucía Fernanda Sánchez Chávez", new BigDecimal("1000.00"), 6, LocalDate.now(clock));

        // 1. Generar contrato (POST /api/contratos/generar) -> 201 CREATED
        String jsonRespuesta = mockMvc.perform(post("/api/contratos/generar")
                        .session(sessionAsesorTrujillo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.numeroContrato").value(Matchers.startsWith("CTR-")))
                .andExpect(jsonPath("$.estado").value("EN_ESPERA"))
                .andExpect(jsonPath("$.impreso").value(false))
                .andExpect(jsonPath("$.tieneContratoFirmado").value(false))
                .andReturn().getResponse().getContentAsString();

        Number idNum = JsonPath.read(jsonRespuesta, "$.id");
        Long id = idNum.longValue();
        String numeroContrato = JsonPath.read(jsonRespuesta, "$.numeroContrato");

        // 2. Ver detalle con cronograma (GET /api/contratos/{id}) -> 200 OK
        mockMvc.perform(get("/api/contratos/" + id)
                        .session(sessionAsesorTrujillo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroContrato").value(numeroContrato))
                .andExpect(jsonPath("$.clienteDni").value("10000007"))
                .andExpect(jsonPath("$.cuotas", Matchers.hasSize(6)));

        // 3. Imprimir contrato (POST /api/contratos/{id}/imprimir) -> 200 OK
        mockMvc.perform(post("/api/contratos/" + id + "/imprimir")
                        .session(sessionAsesorTrujillo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impreso").value(true))
                .andExpect(jsonPath("$.clienteDni").value("10000007"))
                .andExpect(jsonPath("$.clienteNombre").value("Lucía Fernanda Sánchez Chávez"))
                .andExpect(jsonPath("$.fechaImpresion").exists());

        // 4. Reimpresión permitida (POST /api/contratos/{id}/imprimir) -> 200 OK
        mockMvc.perform(post("/api/contratos/" + id + "/imprimir")
                        .session(sessionAsesorTrujillo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteDni").value("10000007"))
                .andExpect(jsonPath("$.impreso").value(true));

        // 5. Subir contrato firmado (POST /api/contratos/{id}/subir-firmado) -> 200 OK
        MockMultipartFile file = new MockMultipartFile("archivo", "contrato_firmado.pdf", "application/pdf", PDF_MAGIC_BYTES);
        mockMvc.perform(multipart("/api/contratos/" + id + "/subir-firmado")
                        .file(file)
                        .param("numeroContrato", numeroContrato)
                        .session(sessionAsesorTrujillo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.tieneContratoFirmado").value(true))
                .andExpect(jsonPath("$.contratoNombreOriginal").value("contrato_firmado.pdf"));

        // 6. Descargar expediente firmado (GET /api/contratos/{id}/descargar) -> 200 OK con Content-Type y nosniff
        mockMvc.perform(get("/api/contratos/" + id + "/descargar")
                        .session(sessionAsesorTrujillo))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.startsWith("inline")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.containsString("contrato_firmado.pdf")))
                .andExpect(content().bytes(PDF_MAGIC_BYTES));
    }

    @Test
    @DisplayName("Restricción de sucursal: Asesor de Chimbote no puede operar sobre contrato de Trujillo")
    void testRestriccionSucursal_asesorOtraSucursalRechazado() throws Exception {
        String json = crearPrestamoJson("10000007", "Lucía Fernanda Sánchez Chávez", new BigDecimal("1000.00"), 6, LocalDate.now(clock));
        String resp = mockMvc.perform(post("/api/contratos/generar")
                        .session(sessionAsesorTrujillo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Number idNum = JsonPath.read(resp, "$.id");
        Long id = idNum.longValue();
        String numeroContrato = JsonPath.read(resp, "$.numeroContrato");

        MockMultipartFile file = new MockMultipartFile("archivo", "contrato.pdf", "application/pdf", PDF_MAGIC_BYTES);

        // 1. Ver detalle rechazado
        mockMvc.perform(get("/api/contratos/" + id)
                        .session(sessionAsesorChimbote))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // 2. Imprimir rechazado
        mockMvc.perform(post("/api/contratos/" + id + "/imprimir")
                        .session(sessionAsesorChimbote))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // 3. Cancelar rechazado
        mockMvc.perform(post("/api/contratos/" + id + "/cancelar")
                        .session(sessionAsesorChimbote))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // 4. Subir firmado rechazado
        mockMvc.perform(multipart("/api/contratos/" + id + "/subir-firmado")
                        .file(file)
                        .param("numeroContrato", numeroContrato)
                        .session(sessionAsesorChimbote))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // 5. Descargar rechazado
        mockMvc.perform(get("/api/contratos/" + id + "/descargar")
                        .session(sessionAsesorChimbote))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Gerente: Acceso global para listar en espera, ver detalle de otra sucursal y descargar")
    void testGerente_accesoGlobal_listaEnEspera_verDetalle_descargar() throws Exception {
        String json = crearPrestamoJson("10000007", "Lucía Fernanda Sánchez Chávez", new BigDecimal("1200.00"), 6, LocalDate.now(clock));
        String resp = mockMvc.perform(post("/api/contratos/generar")
                        .session(sessionAsesorTrujillo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Number idNum = JsonPath.read(resp, "$.id");
        Long id = idNum.longValue();
        String numeroContrato = JsonPath.read(resp, "$.numeroContrato");

        prestamoService.marcarImpreso(id, "asesor.trujillo");
        MockMultipartFile file = new MockMultipartFile("archivo", "contrato_firmado.pdf", "application/pdf", PDF_MAGIC_BYTES);
        prestamoService.subirContratoFirmado(id, numeroContrato, file, "asesor.trujillo");

        // 1. Listar en-espera con sesión de Gerente
        mockMvc.perform(get("/api/contratos/en-espera")
                        .session(sessionGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 2. Ver detalle de préstamo de Trujillo con sesión de Gerente
        mockMvc.perform(get("/api/contratos/" + id)
                        .session(sessionGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroContrato").value(numeroContrato));

        // 3. Descargar contrato firmado con sesión de Gerente
        mockMvc.perform(get("/api/contratos/" + id + "/descargar")
                        .session(sessionGerente))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.startsWith("inline")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.containsString("contrato_firmado.pdf")));
    }

    @Test
    @DisplayName("Cancelar contrato: Borrador antes de imprimir se descarta; tras imprimir exige motivo")
    void testCancelarContrato_descarteBorradorYAnulacionConMotivo() throws Exception {
        // Parte A: Antes de imprimir se descarta el borrador
        String json1 = crearPrestamoJson("10000008", "Ángel Gabriel Peña Morales", new BigDecimal("1000.00"), 6, LocalDate.now(clock));
        String resp1 = mockMvc.perform(post("/api/contratos/generar")
                        .session(sessionAsesorTrujillo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Number idNum1 = JsonPath.read(resp1, "$.id");
        Long id1 = idNum1.longValue();

        mockMvc.perform(post("/api/contratos/" + id1 + "/cancelar")
                        .session(sessionAsesorTrujillo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Borrador de contrato descartado correctamente."));

        assertThat(prestamoRepository.findById(id1)).isEmpty();

        // Parte B: Tras imprimir, rechaza cancelación sin motivo y exige motivo obligatorio
        String json2 = crearPrestamoJson("10000009", "Rosa María Flores Benítez", new BigDecimal("1000.00"), 6, LocalDate.now(clock));
        String resp2 = mockMvc.perform(post("/api/contratos/generar")
                        .session(sessionAsesorTrujillo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json2))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Number idNum2 = JsonPath.read(resp2, "$.id");
        Long id2 = idNum2.longValue();

        prestamoService.marcarImpreso(id2, "asesor.trujillo");

        // Sin motivo -> 400 Bad Request
        mockMvc.perform(post("/api/contratos/" + id2 + "/cancelar")
                        .session(sessionAsesorTrujillo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // Con motivo -> 200 OK y estado ANULADO
        mockMvc.perform(post("/api/contratos/" + id2 + "/cancelar")
                        .session(sessionAsesorTrujillo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Cliente desistió de firmar por tasa de interés\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO"))
                .andExpect(jsonPath("$.motivoAnulacion").value("Cliente desistió de firmar por tasa de interés"));
    }

    @Test
    @DisplayName("Validación tamaño: Subida de archivo de 6 MB devuelve 400 con mensaje exacto")
    void testSubirContratoFirmado_rechazaArchivoMayorA5MB() throws Exception {
        String json = crearPrestamoJson("10000010", "Óscar Iván Gutiérrez Quiroz", new BigDecimal("1000.00"), 6, LocalDate.now(clock));
        String resp = mockMvc.perform(post("/api/contratos/generar")
                        .session(sessionAsesorTrujillo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Number idNum = JsonPath.read(resp, "$.id");
        Long id = idNum.longValue();
        String numeroContrato = JsonPath.read(resp, "$.numeroContrato");

        prestamoService.marcarImpreso(id, "asesor.trujillo");

        // Archivo de 6 MB (6 * 1024 * 1024 bytes) con bytes de inicio de PDF
        byte[] contenido6MB = new byte[6 * 1024 * 1024];
        System.arraycopy(PDF_MAGIC_BYTES, 0, contenido6MB, 0, PDF_MAGIC_BYTES.length);
        MockMultipartFile archivo6MB = new MockMultipartFile("archivo", "contrato_pesado.pdf", "application/pdf", contenido6MB);

        mockMvc.perform(multipart("/api/contratos/" + id + "/subir-firmado")
                        .file(archivo6MB)
                        .param("numeroContrato", numeroContrato)
                        .session(sessionAsesorTrujillo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El archivo excede el tamaño máximo permitido de 5 MB."));
    }

    @Test
    @DisplayName("Validación subida: Número de contrato incorrecto devuelve 400 con error")
    void testSubirContratoFirmado_rechazaNumeroContratoIncorrecto() throws Exception {
        String json = crearPrestamoJson("10000011", "Inés Del Carmen Ortiz Ramos", new BigDecimal("1000.00"), 6, LocalDate.now(clock));
        String resp = mockMvc.perform(post("/api/contratos/generar")
                        .session(sessionAsesorTrujillo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Number idNum = JsonPath.read(resp, "$.id");
        Long id = idNum.longValue();

        prestamoService.marcarImpreso(id, "asesor.trujillo");

        MockMultipartFile file = new MockMultipartFile("archivo", "contrato.pdf", "application/pdf", PDF_MAGIC_BYTES);

        mockMvc.perform(multipart("/api/contratos/" + id + "/subir-firmado")
                        .file(file)
                        .param("numeroContrato", "CTR-2026-999999")
                        .session(sessionAsesorTrujillo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Seguridad DTO: No exponer contratoRuta ni contratoHash en el JSON de respuesta")
    void testSeguridadRespuestaDTO_noExponeRutasNiHashes() throws Exception {
        String json = crearPrestamoJson("10000012", "Héctor Manuel Aguirre Mendoza", new BigDecimal("1000.00"), 6, LocalDate.now(clock));
        String resp = mockMvc.perform(post("/api/contratos/generar")
                        .session(sessionAsesorTrujillo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Number idNum = JsonPath.read(resp, "$.id");
        Long id = idNum.longValue();
        String numeroContrato = JsonPath.read(resp, "$.numeroContrato");

        // 1. Verificación en GET /api/contratos/{id}
        mockMvc.perform(get("/api/contratos/" + id)
                        .session(sessionAsesorTrujillo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contratoRuta").doesNotExist())
                .andExpect(jsonPath("$.contratoHash").doesNotExist())
                .andExpect(jsonPath("$.tieneContratoFirmado").value(false));

        // 2. Verificación en respuesta de POST /api/contratos/{id}/subir-firmado
        prestamoService.marcarImpreso(id, "asesor.trujillo");
        MockMultipartFile file = new MockMultipartFile("archivo", "contrato.pdf", "application/pdf", PDF_MAGIC_BYTES);

        mockMvc.perform(multipart("/api/contratos/" + id + "/subir-firmado")
                        .file(file)
                        .param("numeroContrato", numeroContrato)
                        .session(sessionAsesorTrujillo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contratoRuta").doesNotExist())
                .andExpect(jsonPath("$.contratoHash").doesNotExist())
                .andExpect(jsonPath("$.tieneContratoFirmado").value(true))
                .andExpect(jsonPath("$.contratoNombreOriginal").value("contrato.pdf"));
    }

    @Test
    @DisplayName("Seguridad descarga: Sirve Content-Type detectado por magic bytes y nosniff, ignorando extensión engañosa")
    void testDescargaContratoFirmado_usaContentTypeDeMagicBytes_noDeExtensionNombre() throws Exception {
        String json = crearPrestamoJson("10000013", "Sofía Alejandra Paz Soldán", new BigDecimal("1000.00"), 6, LocalDate.now(clock));
        String resp = mockMvc.perform(post("/api/contratos/generar")
                        .session(sessionAsesorTrujillo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Number idNum = JsonPath.read(resp, "$.id");
        Long id = idNum.longValue();
        String numeroContrato = JsonPath.read(resp, "$.numeroContrato");

        prestamoService.marcarImpreso(id, "asesor.trujillo");

        // Archivo con nombre "documento.png", pero con bytes genuinos de PDF
        MockMultipartFile fileEnganoso = new MockMultipartFile("archivo", "documento.png", "image/png", PDF_MAGIC_BYTES);

        mockMvc.perform(multipart("/api/contratos/" + id + "/subir-firmado")
                        .file(fileEnganoso)
                        .param("numeroContrato", numeroContrato)
                        .session(sessionAsesorTrujillo))
                .andExpect(status().isOk());

        // Al descargar, debe servirse como application/pdf (según magic bytes) y con X-Content-Type-Options: nosniff
        mockMvc.perform(get("/api/contratos/" + id + "/descargar")
                        .session(sessionAsesorTrujillo))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.startsWith("inline")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.containsString("documento.png")))
                .andExpect(content().bytes(PDF_MAGIC_BYTES));
    }
}
