package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.pagaopiu.dto.PanelResponseDTO;
import com.example.pagaopiu.dto.PanelSucursalDTO;
import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PagoRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class PanelIntegrationTest {

    @Autowired
    private PanelService panelService;

    @Autowired
    private PrestamoService prestamoService;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private EstadoService estadoService;

    @Autowired
    private PrestamoRepository prestamoRepository;

    @Autowired
    private CuotaRepository cuotaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private Clock clock;

    private MockMvc mockMvc;

    @BeforeEach
    void limpiarBaseDatos() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        cuotaRepository.deleteAll();
        pagoRepository.deleteAll();
        prestamoRepository.deleteAll();
        clienteRepository.deleteAll();
    }

    private MockHttpSession crearSesion(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, "password123", userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private Prestamo crearPrestamoDemo(String dni, String nombre, BigDecimal monto, int plazo, String usernameAsesor) {
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(dni)
                .nombreCliente(nombre)
                .monto(monto)
                .plazoMeses(plazo)
                .fechaPrestamo(LocalDate.now(clock))
                .build();
        return prestamoService.registrarPrestamo(dto, usernameAsesor);
    }

    @Test
    @DisplayName("Seguridad R14: Gerente puede acceder a /api/panel y a /panel")
    void testSeguridad_GerentePuedeAccederAPanel() throws Exception {
        MockHttpSession sessionGerente = crearSesion("gerente");

        mockMvc.perform(get("/api/panel").session(sessionGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucursales").isArray());

        mockMvc.perform(get("/panel").session(sessionGerente))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Seguridad R14: Asesor recibe 403 Forbidden al intentar acceder a /api/panel y a /panel")
    void testSeguridad_AsesorRecibe403EnPanel() throws Exception {
        MockHttpSession sessionAsesor = crearSesion("asesor.trujillo");

        mockMvc.perform(get("/api/panel").session(sessionAsesor))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/panel").session(sessionAsesor))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Seguridad R14: Usuario anónimo recibe 401 en /api/panel y en /panel")
    void testSeguridad_AnonimoRecibe401oRedireccion() throws Exception {
        mockMvc.perform(get("/api/panel"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/panel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("R14: Métricas por sucursal de origen - Préstamos activos se computan en la sucursal donde se originó")
    void testMetricasSucursalOrigen() {
        // Préstamo originado en Trujillo
        crearPrestamoDemo("27427864", "CASTILLO TERRONES, JOSE PEDRO", new BigDecimal("1000.00"), 12, "asesor.trujillo");
        // Préstamo originado en Piura
        crearPrestamoDemo("10000001", "Juan Carlos Pérez López", new BigDecimal("2000.00"), 6, "asesor.piura");

        PanelResponseDTO panel = panelService.obtenerMetricasPanel();

        assertThat(panel).isNotNull();
        assertThat(panel.getTotalPrestamosActivos()).isEqualTo(2);

        PanelSucursalDTO tru = panel.getSucursales().stream()
                .filter(s -> s.getSucursalNombre().equals("Trujillo")).findFirst().orElseThrow();
        PanelSucursalDTO piu = panel.getSucursales().stream()
                .filter(s -> s.getSucursalNombre().equals("Piura")).findFirst().orElseThrow();
        PanelSucursalDTO chi = panel.getSucursales().stream()
                .filter(s -> s.getSucursalNombre().equals("Chiclayo")).findFirst().orElseThrow();

        assertThat(tru.getPrestamosActivos()).isEqualTo(1);
        assertThat(tru.getPrestamosVigentes()).isEqualTo(1);
        assertThat(piu.getPrestamosActivos()).isEqualTo(1);
        assertThat(piu.getPrestamosVigentes()).isEqualTo(1);
        assertThat(chi.getPrestamosActivos()).isEqualTo(0);
    }

    @Test
    @DisplayName("R14: Métricas por sucursal de pago cruzado - El pago e intereses se computan en la sucursal que cobró")
    void testMetricasSucursalPagoCruzado() {
        // Préstamo originado en Trujillo
        Prestamo prestamo = crearPrestamoDemo("10000002", "María Elena García Ríos", new BigDecimal("1000.00"), 12, "asesor.trujillo");

        // Pago efectuado en Piura por asesor.piura
        pagoService.pagarSiguienteCuota(prestamo.getId(), "asesor.piura");

        PanelResponseDTO panel = panelService.obtenerMetricasPanel();

        PanelSucursalDTO tru = panel.getSucursales().stream()
                .filter(s -> s.getSucursalNombre().equals("Trujillo")).findFirst().orElseThrow();
        PanelSucursalDTO piu = panel.getSucursales().stream()
                .filter(s -> s.getSucursalNombre().equals("Piura")).findFirst().orElseThrow();

        // Trujillo originó el préstamo (1 activo), pero cobró S/ 0.00
        assertThat(tru.getPrestamosActivos()).isEqualTo(1);
        assertThat(tru.getMontoTotalCobrado()).isEqualByComparingTo("0.00");
        assertThat(tru.getInteresesCobrados()).isEqualByComparingTo("0.00");

        // Piura no originó el préstamo (0 activos), pero cobró la cuota (S/ 95.79) y su interés (S/ 22.10)
        assertThat(piu.getPrestamosActivos()).isEqualTo(0);
        assertThat(piu.getMontoTotalCobrado()).isEqualByComparingTo("95.79");
        assertThat(piu.getInteresesCobrados()).isEqualByComparingTo("22.10");

        // Totales consolidados globales
        assertThat(panel.getTotalMontoCobrado()).isEqualByComparingTo("95.79");
        assertThat(panel.getTotalInteresesCobrados()).isEqualByComparingTo("22.10");
    }

    @Test
    @DisplayName("R14: Métricas globales de clientes morosos y extrajudiciales")
    void testMetricasGlobalesClientesMorososYExtrajudiciales() {
        LocalDate hoy = LocalDate.now(clock);

        // Cliente 1 en Trujillo con cuota vencida hace 10 días -> MOROSO
        Prestamo p1 = crearPrestamoDemo("10000004", "Ana Belén Gómez Díaz", new BigDecimal("1000.00"), 12, "asesor.trujillo");
        Cuota c1 = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(p1.getId()).get(0);
        c1.setFechaVencimiento(hoy.minusDays(10));
        cuotaRepository.save(c1);

        // Cliente 2 en Piura con cuota vencida hace 370 días -> EXTRAJUDICIAL
        Prestamo p2 = crearPrestamoDemo("10000005", "Víctor Raúl Haya De La Torre", new BigDecimal("1000.00"), 12, "asesor.piura");
        Cuota c2 = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(p2.getId()).get(0);
        c2.setFechaVencimiento(hoy.minusDays(370));
        cuotaRepository.save(c2);

        // Cliente 3 en Chiclayo al día -> ACTIVO / VIGENTE
        crearPrestamoDemo("10000006", "César Abraham Vallejo Mendoza", new BigDecimal("1000.00"), 12, "asesor.chiclayo");

        // Ejecutar recálculo nocturno de estados
        estadoService.recalcularTodos();

        PanelResponseDTO panel = panelService.obtenerMetricasPanel();

        assertThat(panel.getClientesMorosos()).isEqualTo(1);
        assertThat(panel.getClientesExtrajudiciales()).isEqualTo(1);
        assertThat(panel.getTotalPrestamosMorosos()).isEqualTo(1);
        assertThat(panel.getTotalPrestamosExtrajudiciales()).isEqualTo(1);
        assertThat(panel.getTotalPrestamosVigentes()).isEqualTo(1);
        assertThat(panel.getTotalPrestamosActivos()).isEqualTo(3);

        // Desglose por sucursal de origen
        PanelSucursalDTO tru = panel.getSucursales().stream()
                .filter(s -> s.getSucursalNombre().equals("Trujillo")).findFirst().orElseThrow();
        PanelSucursalDTO piu = panel.getSucursales().stream()
                .filter(s -> s.getSucursalNombre().equals("Piura")).findFirst().orElseThrow();
        PanelSucursalDTO chi = panel.getSucursales().stream()
                .filter(s -> s.getSucursalNombre().equals("Chiclayo")).findFirst().orElseThrow();

        assertThat(tru.getPrestamosMorosos()).isEqualTo(1);
        assertThat(piu.getPrestamosExtrajudiciales()).isEqualTo(1);
        assertThat(chi.getPrestamosVigentes()).isEqualTo(1);
    }

    @Test
    @DisplayName("R14: Consolidado de totales coincide exactamente con la suma individual de las 5 sucursales")
    void testConsolidadoTotalesCoincidenConSumaSucursales() {
        // Generar operaciones variadas
        Prestamo p1 = crearPrestamoDemo("10000007", "Lucía Fernanda Sánchez Chávez", new BigDecimal("1000.00"), 12, "asesor.trujillo");
        Prestamo p2 = crearPrestamoDemo("10000008", "Ángel Gabriel Peña Morales", new BigDecimal("500.00"), 6, "asesor.piura");

        // Pagar 1 cuota en p1 y cancelar anticipado p2
        pagoService.pagarSiguienteCuota(p1.getId(), "asesor.trujillo");
        pagoService.cancelarAnticipado(p2.getId(), "asesor.tumbes");

        PanelResponseDTO panel = panelService.obtenerMetricasPanel();

        long sumaActivos = panel.getSucursales().stream().mapToLong(PanelSucursalDTO::getPrestamosActivos).sum();
        long sumaVigentes = panel.getSucursales().stream().mapToLong(PanelSucursalDTO::getPrestamosVigentes).sum();
        long sumaMorosos = panel.getSucursales().stream().mapToLong(PanelSucursalDTO::getPrestamosMorosos).sum();
        long sumaExtra = panel.getSucursales().stream().mapToLong(PanelSucursalDTO::getPrestamosExtrajudiciales).sum();

        BigDecimal sumaCobrado = panel.getSucursales().stream()
                .map(PanelSucursalDTO::getMontoTotalCobrado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sumaIntereses = panel.getSucursales().stream()
                .map(PanelSucursalDTO::getInteresesCobrados)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(panel.getTotalPrestamosActivos()).isEqualTo(sumaActivos);
        assertThat(panel.getTotalPrestamosVigentes()).isEqualTo(sumaVigentes);
        assertThat(panel.getTotalPrestamosMorosos()).isEqualTo(sumaMorosos);
        assertThat(panel.getTotalPrestamosExtrajudiciales()).isEqualTo(sumaExtra);
        assertThat(panel.getTotalMontoCobrado()).isEqualByComparingTo(sumaCobrado);
        assertThat(panel.getTotalInteresesCobrados()).isEqualByComparingTo(sumaIntereses);
    }

    @Test
    @DisplayName("R14: Métricas de dinero colocado, cantidad de clientes prestatarios y filtro por sede")
    void testMetricasColocacionYCantidadClientesPrestatariosPorSedeYGlobal() throws Exception {
        // Crear 2 préstamos en Trujillo para 2 clientes distintos
        Prestamo p1 = crearPrestamoDemo("10000001", "Juan Carlos Pérez López", new BigDecimal("2000.00"), 12, "asesor.trujillo");
        Prestamo p2 = crearPrestamoDemo("10000002", "María Elena García Ríos", new BigDecimal("3000.00"), 12, "asesor.trujillo");

        // Crear 1 préstamo en Piura
        Prestamo p3 = crearPrestamoDemo("10000003", "José Antonio Núñez Ávila", new BigDecimal("1500.00"), 6, "asesor.piura");

        PanelResponseDTO panel = panelService.obtenerMetricasPanel();

        // Verificación de totales globales consolidados
        assertThat(panel.getTotalMontoColocado()).isEqualByComparingTo("6500.00");
        assertThat(panel.getTotalClientes()).isEqualTo(3);

        // Verificación por sede individual
        PanelSucursalDTO tru = panel.getSucursales().stream()
                .filter(s -> s.getSucursalNombre().equals("Trujillo")).findFirst().orElseThrow();
        PanelSucursalDTO piu = panel.getSucursales().stream()
                .filter(s -> s.getSucursalNombre().equals("Piura")).findFirst().orElseThrow();
        PanelSucursalDTO chi = panel.getSucursales().stream()
                .filter(s -> s.getSucursalNombre().equals("Chiclayo")).findFirst().orElseThrow();

        assertThat(tru.getMontoTotalColocado()).isEqualByComparingTo("5000.00");
        assertThat(tru.getCantidadClientes()).isEqualTo(2);

        assertThat(piu.getMontoTotalColocado()).isEqualByComparingTo("1500.00");
        assertThat(piu.getCantidadClientes()).isEqualTo(1);

        assertThat(chi.getMontoTotalColocado()).isEqualByComparingTo("0.00");
        assertThat(chi.getCantidadClientes()).isEqualTo(0);

        // Probar endpoint REST /api/prestamo/recientes filtrado por sede Trujillo
        MockHttpSession sesionGerente = crearSesion("gerente");
        mockMvc.perform(get("/api/prestamo/recientes?sucursalId=" + tru.getSucursalId())
                        .session(sesionGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sucursalNombre").value("Trujillo"))
                .andExpect(jsonPath("$[1].sucursalNombre").value("Trujillo"));
    }
}
