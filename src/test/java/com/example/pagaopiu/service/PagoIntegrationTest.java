package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.exception.NegocioException;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Pago;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.enums.EstadoCuota;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import com.example.pagaopiu.model.enums.TipoPago;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PagoRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class PagoIntegrationTest {

    @Autowired
    private PagoService pagoService;

    @Autowired
    private PrestamoService prestamoService;

    @Autowired
    private PrestamoRepository prestamoRepository;

    @Autowired
    private CuotaRepository cuotaRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private com.example.pagaopiu.security.CustomUserDetailsService userDetailsService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private Clock clock;

    @Autowired
    private com.example.pagaopiu.TestDatabaseCleaner databaseCleaner;

    private MockMvc mockMvc;

    @BeforeEach
    void limpiarBaseDatos() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        databaseCleaner.clean();
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
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
    @DisplayName("R10: Pago secuencial de cuota - Cuota 1 pasa a PAGADA, asociada a Pago tipo CUOTA, cuotas restantes PENDIENTES")
    void testPagoSecuencialCuota() {
        Prestamo prestamo = crearPrestamoDemo("27427864", "CASTILLO TERRONES, JOSE PEDRO", new BigDecimal("1000.00"), 12, "asesor.trujillo");

        Pago pago = pagoService.pagarSiguienteCuota(prestamo.getId(), "asesor.trujillo");

        assertThat(pago).isNotNull();
        assertThat(pago.getId()).isNotNull();
        assertThat(pago.getTipo()).isEqualTo(TipoPago.CUOTA);
        assertThat(pago.getMonto()).isEqualByComparingTo(new BigDecimal("95.79"));
        assertThat(pago.getSucursal().getNombre()).isEqualTo("Trujillo");
        assertThat(pago.getUsuario().getUsername()).isEqualTo("asesor.trujillo");
        assertThat(pago.getFechaPago()).isEqualTo(LocalDate.now(clock));

        // Verificar cuotas
        List<Cuota> cuotas = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamo.getId());
        assertThat(cuotas.get(0).getEstado()).isEqualTo(EstadoCuota.PAGADA);
        assertThat(cuotas.get(0).getPago().getId()).isEqualTo(pago.getId());

        for (int i = 1; i < 12; i++) {
            assertThat(cuotas.get(i).getEstado()).isEqualTo(EstadoCuota.PENDIENTE);
            assertThat(cuotas.get(i).getPago()).isNull();
        }

        Prestamo actual = prestamoRepository.findById(prestamo.getId()).orElseThrow();
        assertThat(actual.getEstado()).isEqualTo(EstadoPrestamo.VIGENTE);
        assertThat(actual.getFechaCancelacion()).isNull();
    }

    @Test
    @DisplayName("R11: Cobro intersucursal - Préstamo originado en Trujillo se cobra por asesor de Chimbote (sucursal pago = Chimbote)")
    void testCobroIntersucursal() {
        Prestamo prestamo = crearPrestamoDemo("10000001", "Juan Carlos Pérez López", new BigDecimal("500.00"), 6, "asesor.trujillo");
        assertThat(prestamo.getSucursal().getNombre()).isEqualTo("Trujillo");

        // Cobro realizado por asesor de Chimbote
        Pago pago = pagoService.pagarSiguienteCuota(prestamo.getId(), "asesor.chimbote");

        assertThat(pago.getSucursal().getNombre()).isEqualTo("Chimbote");
        assertThat(pago.getUsuario().getUsername()).isEqualTo("asesor.chimbote");
    }

    @Test
    @DisplayName("R10: Pago secuencial hasta la última cuota - Préstamo pasa a CANCELADO con fecha de cancelación")
    void testCancelacionAlCompletarUltimaCuota() {
        Prestamo prestamo = crearPrestamoDemo("10000002", "María Elena García Ríos", new BigDecimal("1.00"), 1, "asesor.chiclayo");

        Pago pago = pagoService.pagarSiguienteCuota(prestamo.getId(), "asesor.chiclayo");
        assertThat(pago).isNotNull();

        Prestamo cancelado = prestamoRepository.findById(prestamo.getId()).orElseThrow();
        assertThat(cancelado.getEstado()).isEqualTo(EstadoPrestamo.CANCELADO);
        assertThat(cancelado.getFechaCancelacion()).isEqualTo(LocalDate.now(clock));

        Cuota cuotaUnica = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamo.getId()).get(0);
        assertThat(cuotaUnica.getEstado()).isEqualTo(EstadoCuota.PAGADA);
    }

    @Test
    @DisplayName("R12: Cancelación anticipada - Paga la suma total de cuotas pendientes en un solo pago y cancela préstamo")
    void testCancelacionAnticipada() {
        Prestamo prestamo = crearPrestamoDemo("10000004", "Ana Belén Gómez Díaz", new BigDecimal("1000.00"), 12, "asesor.piura");

        List<Cuota> cuotasAntes = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamo.getId());
        BigDecimal sumaEsperada = cuotasAntes.stream().map(Cuota::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);

        Pago pago = pagoService.cancelarAnticipado(prestamo.getId(), "asesor.tumbes");

        assertThat(pago).isNotNull();
        assertThat(pago.getTipo()).isEqualTo(TipoPago.CANCELACION_ANTICIPADA);
        assertThat(pago.getMonto()).isEqualByComparingTo(sumaEsperada);
        assertThat(pago.getSucursal().getNombre()).isEqualTo("Tumbes");

        // Todas las cuotas pasan a PAGADA
        List<Cuota> cuotasDespues = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamo.getId());
        for (Cuota c : cuotasDespues) {
            assertThat(c.getEstado()).isEqualTo(EstadoCuota.PAGADA);
            assertThat(c.getPago().getId()).isEqualTo(pago.getId());
        }

        Prestamo prestamoCancelado = prestamoRepository.findById(prestamo.getId()).orElseThrow();
        assertThat(prestamoCancelado.getEstado()).isEqualTo(EstadoPrestamo.CANCELADO);
        assertThat(prestamoCancelado.getFechaCancelacion()).isEqualTo(LocalDate.now(clock));
    }

    @Test
    @DisplayName("Rechazo de pago o cancelación en préstamo ya CANCELADO")
    void testRechazoEnPrestamoYaCancelado() {
        Prestamo prestamo = crearPrestamoDemo("10000005", "Víctor Raúl Haya De La Torre", new BigDecimal("500.00"), 6, "asesor.trujillo");
        pagoService.cancelarAnticipado(prestamo.getId(), "asesor.trujillo");

        NegocioException exCuota = assertThrows(NegocioException.class,
                () -> pagoService.pagarSiguienteCuota(prestamo.getId(), "asesor.trujillo"));
        assertThat(exCuota.getMessage()).contains("ya se encuentra cancelado");

        NegocioException exCanc = assertThrows(NegocioException.class,
                () -> pagoService.cancelarAnticipado(prestamo.getId(), "asesor.trujillo"));
        assertThat(exCanc.getMessage()).contains("ya se encuentra cancelado");
    }

    @Test
    @DisplayName("Rechazo de pago en préstamo con estado EXTRAJUDICIAL")
    void testRechazoEnPrestamoExtrajudicial() {
        Prestamo prestamo = crearPrestamoDemo("10000006", "César Abraham Vallejo Mendoza", new BigDecimal("800.00"), 6, "asesor.trujillo");
        prestamo.setEstado(EstadoPrestamo.EXTRAJUDICIAL);
        prestamoRepository.save(prestamo);

        NegocioException ex = assertThrows(NegocioException.class,
                () -> pagoService.pagarSiguienteCuota(prestamo.getId(), "asesor.trujillo"));
        assertThat(ex.getMessage()).contains("EXTRAJUDICIAL y no admite pagos");
    }

    @Test
    @DisplayName("Seguridad: GERENTE recibe 403 en endpoints /api/pago/**; petición no autenticada recibe 401")
    void testSeguridadPagos() throws Exception {
        Prestamo prestamo = crearPrestamoDemo("10000007", "Lucía Fernanda Sánchez Chávez", new BigDecimal("600.00"), 6, "asesor.trujillo");

        // 1. Petición sin autenticación recibe 401
        mockMvc.perform(post("/api/pago/cuota/" + prestamo.getId()))
                .andExpect(status().isUnauthorized());

        // 2. GERENTE recibe 403 Forbidden
        UserDetails gerenteDetails = userDetailsService.loadUserByUsername("gerente");
        Authentication authGerente = new UsernamePasswordAuthenticationToken(gerenteDetails, "password123", gerenteDetails.getAuthorities());
        SecurityContext contextGerente = SecurityContextHolder.createEmptyContext();
        contextGerente.setAuthentication(authGerente);

        MockHttpSession sessionGerente = new MockHttpSession();
        sessionGerente.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contextGerente);

        mockMvc.perform(post("/api/pago/cuota/" + prestamo.getId()).session(sessionGerente))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Concurrencia en pago: dos solicitudes simultáneas pagan cuota 1 y cuota 2 secuencialmente sin duplicidad")
    void testConcurrenciaPagoCuotas() throws Exception {
        Prestamo prestamo = crearPrestamoDemo("10000008", "Ángel Gabriel Peña Morales", new BigDecimal("1000.00"), 12, "asesor.trujillo");

        int numHilos = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numHilos);
        CountDownLatch readyLatch = new CountDownLatch(numHilos);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger exitos = new AtomicInteger(0);
        List<Throwable> errores = Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < numHilos; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    pagoService.pagarSiguienteCuota(prestamo.getId(), "asesor.trujillo");
                    exitos.incrementAndGet();
                } catch (Throwable t) {
                    errores.add(t);
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Ambos hilos deben tener éxito gracias al lock pesimista
        assertThat(exitos.get()).isEqualTo(2);
        assertThat(errores).isEmpty();

        List<Cuota> cuotas = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamo.getId());
        // Cuota 1 y cuota 2 deben estar PAGADAS
        assertThat(cuotas.get(0).getEstado()).isEqualTo(EstadoCuota.PAGADA);
        assertThat(cuotas.get(1).getEstado()).isEqualTo(EstadoCuota.PAGADA);
        // Cuota 3 debe seguir PENDIENTE
        assertThat(cuotas.get(2).getEstado()).isEqualTo(EstadoCuota.PENDIENTE);

        // Los pagos deben ser 2 con IDs distintos
        assertThat(cuotas.get(0).getPago().getId()).isNotEqualTo(cuotas.get(1).getPago().getId());
    }

    @Test
    @DisplayName("Emisión de comprobante con correlativo único B001 para persona natural")
    void testEmisionComprobante_Unico() {
        Prestamo prestamo = crearPrestamoDemo("27427864", "CASTILLO TERRONES, JOSE PEDRO", new BigDecimal("1000.00"), 12, "asesor.trujillo");

        Pago pago1 = pagoService.pagarSiguienteCuota(prestamo.getId(), "asesor.trujillo");
        assertThat(pago1.getNumeroComprobante()).isNotNull();
        assertThat(pago1.getNumeroComprobante()).startsWith("B001-");
        assertThat(pago1.getTipoComprobante()).isEqualTo("BOLETA");

        Pago pago2 = pagoService.pagarSiguienteCuota(prestamo.getId(), "asesor.trujillo");
        assertThat(pago2.getNumeroComprobante()).isNotNull();
        assertThat(pago2.getNumeroComprobante()).startsWith("B001-");
        assertThat(pago2.getNumeroComprobante()).isNotEqualTo(pago1.getNumeroComprobante());
    }
}
