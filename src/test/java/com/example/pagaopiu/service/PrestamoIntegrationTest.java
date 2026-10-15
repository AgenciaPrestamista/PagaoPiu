package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.pagaopiu.dto.CuotaDetalle;
import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.exception.NegocioException;
import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.entity.ReniecPadron;
import com.example.pagaopiu.model.enums.EstadoCliente;
import com.example.pagaopiu.model.enums.EstadoCuota;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PagoRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import com.example.pagaopiu.repository.UsuarioRepository;
import com.example.pagaopiu.service.impl.ClienteRegistroHelper;
import com.example.pagaopiu.service.impl.PrestamoServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class PrestamoIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @Autowired
    private PrestamoService prestamoService;

    @Autowired
    private PrestamoRepository prestamoRepository;

    @Autowired
    private CuotaRepository cuotaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ReniecClient reniecClient;

    @Autowired
    private ClienteRegistroHelper clienteRegistroHelper;

    @Autowired
    private Clock clock;

    private final CronogramaCalculator calculator = new CronogramaCalculator();

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

    @Test
    @DisplayName("Camino feliz: préstamo y 12 cuotas idénticas al calculador; cliente creado con nombre del padrón")
    void testCaminoFeliz() {
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("27427864")
                .nombreCliente("CASTILLO TERRONES, JOSE PEDRO")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(LocalDate.now(clock))
                .build();

        Prestamo prestamo = prestamoService.registrarPrestamo(dto, "asesor.trujillo");

        assertThat(prestamo).isNotNull();
        assertThat(prestamo.getId()).isNotNull();
        assertThat(prestamo.getCapital()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(prestamo.getPlazoMeses()).isEqualTo(12);
        assertThat(prestamo.getTea()).isEqualByComparingTo(new BigDecimal("0.30"));
        assertThat(prestamo.getCuotaFija()).isEqualByComparingTo(new BigDecimal("95.79"));
        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.VIGENTE);
        assertThat(prestamo.getSucursal().getNombre()).isEqualTo("Trujillo");
        assertThat(prestamo.getAsesor().getUsername()).isEqualTo("asesor.trujillo");

        // Verificar creación de cliente en BD
        Optional<Cliente> clienteOpt = clienteRepository.findByDni("27427864");
        assertThat(clienteOpt).isPresent();
        assertThat(clienteOpt.get().getNombreCompleto()).isEqualTo("CASTILLO TERRONES, JOSE PEDRO");
        assertThat(clienteOpt.get().getEstado()).isEqualTo(EstadoCliente.ACTIVO);

        // Verificar 12 cuotas
        List<Cuota> cuotas = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamo.getId());
        assertThat(cuotas).hasSize(12);

        List<CuotaDetalle> cronogramaEsperado = calculator.generarCronograma(
                prestamo.getCapital(),
                prestamo.getPlazoMeses(),
                prestamo.getFechaPrestamo(),
                prestamo.getTea()
        );

        for (int i = 0; i < 12; i++) {
            Cuota real = cuotas.get(i);
            CuotaDetalle esperado = cronogramaEsperado.get(i);

            assertThat(real.getNumero()).isEqualTo(esperado.numero());
            assertThat(real.getFechaVencimiento()).isEqualTo(esperado.fechaVencimiento());
            assertThat(real.getMonto()).isEqualByComparingTo(esperado.monto());
            assertThat(real.getInteres()).isEqualByComparingTo(esperado.interes());
            assertThat(real.getCapital()).isEqualByComparingTo(esperado.capital());
            assertThat(real.getSaldoCapital()).isEqualByComparingTo(esperado.saldoCapital());
            assertThat(real.getEstado()).isEqualTo(EstadoCuota.PENDIENTE);
        }

        // Aserciones específicas de la cuota 1 y última cuota
        assertThat(cuotas.get(0).getInteres()).isEqualByComparingTo(new BigDecimal("22.10"));
        assertThat(cuotas.get(0).getCapital()).isEqualByComparingTo(new BigDecimal("73.69"));
        assertThat(cuotas.get(0).getMonto()).isEqualByComparingTo(new BigDecimal("95.79"));
        assertThat(cuotas.get(11).getSaldoCapital()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("R2: DNI inexistente, DNI no vigente, fallecido y extranjero son rechazados")
    void testR2_ValidacionesReniec() {
        LocalDate hoy = LocalDate.now(clock);

        // DNI inexistente
        PrestamoRegistroDTO inex = PrestamoRegistroDTO.builder()
                .dni("00000000")
                .nombreCliente("Cualquier Nombre")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        NegocioException ex1 = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(inex, "asesor.trujillo"));
        assertThat(ex1.getMessage()).contains("no se encuentra registrado");

        // DNI no vigente (99999902)
        PrestamoRegistroDTO noVig = PrestamoRegistroDTO.builder()
                .dni("99999902")
                .nombreCliente("Ana DNI Vencido Ejemplo")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        NegocioException ex2 = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(noVig, "asesor.trujillo"));
        assertThat(ex2.getMessage()).contains("no se encuentra vigente");

        // Fallecido (99999901)
        PrestamoRegistroDTO fall = PrestamoRegistroDTO.builder()
                .dni("99999901")
                .nombreCliente("Carlos Muerto Ejemplo")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        NegocioException ex3 = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(fall, "asesor.trujillo"));
        assertThat(ex3.getMessage()).contains("fallecida");

        // Extranjero (99999903)
        PrestamoRegistroDTO extr = PrestamoRegistroDTO.builder()
                .dni("99999903")
                .nombreCliente("John Smith Foreign")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        NegocioException ex4 = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(extr, "asesor.trujillo"));
        assertThat(ex4.getMessage()).contains("nacionalidad peruana");
    }

    @Test
    @DisplayName("R3: Nombre con tilde cambiada y nombre en minúsculas son ambos rechazados")
    void testR3_ValidacionNombreExacto() {
        LocalDate hoy = LocalDate.now(clock);

        // Nombre con tildes cambiadas (Jose Antonio Nuñez Avila en vez de José Antonio Núñez Ávila)
        PrestamoRegistroDTO dtoTilde = PrestamoRegistroDTO.builder()
                .dni("10000003")
                .nombreCliente("Jose Antonio Nuñez Avila")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        NegocioException ex1 = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoTilde, "asesor.trujillo"));
        assertThat(ex1.getMessage()).contains("no coincide exactamente");

        // Nombre en minúsculas
        PrestamoRegistroDTO dtoMinus = PrestamoRegistroDTO.builder()
                .dni("10000003")
                .nombreCliente("josé antonio núñez ávila")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        NegocioException ex2 = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoMinus, "asesor.trujillo"));
        assertThat(ex2.getMessage()).contains("no coincide exactamente");
    }

    @Test
    @DisplayName("R4 y R5: Plazo 0 y 61 rechazados; monto 0 y 10001 rechazados; monto 1 aceptado")
    void testR4_R5_LimitesPlazoYMonto() {
        LocalDate hoy = LocalDate.now(clock);

        // Plazo 0
        PrestamoRegistroDTO dtoP0 = PrestamoRegistroDTO.builder()
                .dni("10000001")
                .nombreCliente("Juan Carlos Pérez López")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(0)
                .fechaPrestamo(hoy)
                .build();
        assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoP0, "asesor.chimbote"));

        // Plazo 61
        PrestamoRegistroDTO dtoP61 = PrestamoRegistroDTO.builder()
                .dni("10000001")
                .nombreCliente("Juan Carlos Pérez López")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(61)
                .fechaPrestamo(hoy)
                .build();
        assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoP61, "asesor.chimbote"));

        // Monto 0
        PrestamoRegistroDTO dtoM0 = PrestamoRegistroDTO.builder()
                .dni("10000001")
                .nombreCliente("Juan Carlos Pérez López")
                .monto(BigDecimal.ZERO)
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoM0, "asesor.chimbote"));

        // Monto 10001
        PrestamoRegistroDTO dtoM10001 = PrestamoRegistroDTO.builder()
                .dni("10000001")
                .nombreCliente("Juan Carlos Pérez López")
                .monto(new BigDecimal("10001.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoM10001, "asesor.chimbote"));

        // Monto 1 aceptado (mínimo de pruebas)
        PrestamoRegistroDTO dtoM1 = PrestamoRegistroDTO.builder()
                .dni("10000001")
                .nombreCliente("Juan Carlos Pérez López")
                .monto(new BigDecimal("1.00"))
                .plazoMeses(1)
                .fechaPrestamo(hoy)
                .build();
        Prestamo prestamoM1 = prestamoService.registrarPrestamo(dtoM1, "asesor.chimbote");
        assertThat(prestamoM1).isNotNull();
        assertThat(prestamoM1.getCapital()).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    @Test
    @DisplayName("R8: Fecha futura rechazada; fecha retroactiva aceptada")
    void testR8_FechaFuturaYRetroactiva() {
        LocalDate hoy = LocalDate.now(clock);

        // Fecha futura
        PrestamoRegistroDTO dtoFuturo = PrestamoRegistroDTO.builder()
                .dni("10000002")
                .nombreCliente("María Elena García Ríos")
                .monto(new BigDecimal("500.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy.plusDays(1))
                .build();
        NegocioException ex = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoFuturo, "asesor.chiclayo"));
        assertThat(ex.getMessage()).contains("no puede ser futura");

        // Fecha retroactiva
        PrestamoRegistroDTO dtoPasado = PrestamoRegistroDTO.builder()
                .dni("10000002")
                .nombreCliente("María Elena García Ríos")
                .monto(new BigDecimal("500.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy.minusDays(5))
                .build();
        Prestamo prestamoPasado = prestamoService.registrarPrestamo(dtoPasado, "asesor.chiclayo");
        assertThat(prestamoPasado).isNotNull();
        assertThat(prestamoPasado.getFechaPrestamo()).isEqualTo(hoy.minusDays(5));
    }

    @Test
    @DisplayName("R6: Tope: 6000 y luego 5000 se rechaza; 6000 y luego 4000 aceptado (saldo exacto 10000)")
    void testR6_TopeDeuda() {
        LocalDate hoy = LocalDate.now(clock);

        // Primer préstamo: 6000
        PrestamoRegistroDTO p1 = PrestamoRegistroDTO.builder()
                .dni("10000004")
                .nombreCliente("Ana Belén Gómez Díaz")
                .monto(new BigDecimal("6000.00"))
                .plazoMeses(24)
                .fechaPrestamo(hoy)
                .build();
        Prestamo res1 = prestamoService.registrarPrestamo(p1, "asesor.piura");
        assertThat(res1).isNotNull();

        // Segundo préstamo: 5000 -> rechaza (6000 + 5000 = 11000 > 10000)
        PrestamoRegistroDTO p2 = PrestamoRegistroDTO.builder()
                .dni("10000004")
                .nombreCliente("Ana Belén Gómez Díaz")
                .monto(new BigDecimal("5000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        NegocioException ex = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(p2, "asesor.piura"));
        assertThat(ex.getMessage()).contains("excede el tope de deuda");
        assertThat(ex.getMessage()).contains("Cupo restante disponible: S/ 4000.00");

        // Tercer préstamo: 4000 -> aceptado (6000 + 4000 = 10000 exacto)
        PrestamoRegistroDTO p3 = PrestamoRegistroDTO.builder()
                .dni("10000004")
                .nombreCliente("Ana Belén Gómez Díaz")
                .monto(new BigDecimal("4000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        Prestamo res3 = prestamoService.registrarPrestamo(p3, "asesor.piura");
        assertThat(res3).isNotNull();
        assertThat(res3.getCapital()).isEqualByComparingTo(new BigDecimal("4000.00"));
    }

    @Test
    @DisplayName("R7: Cliente con estado MOROSO puesto manualmente en base de datos es rechazado")
    void testR7_ClienteMorosoRechazado() {
        LocalDate hoy = LocalDate.now(clock);

        // Insertar cliente previo con estado MOROSO
        Cliente clienteMoroso = clienteRepository.saveAndFlush(Cliente.builder()
                .dni("10000005")
                .nombreCompleto("Víctor Raúl Haya De La Torre")
                .estado(EstadoCliente.MOROSO)
                .build());

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000005")
                .nombreCliente("Víctor Raúl Haya De La Torre")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();

        NegocioException ex = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dto, "asesor.trujillo"));
        assertThat(ex.getMessage()).contains("MOROSO");
    }

    @Test
    @DisplayName("Atomicidad: si falla la generación de cuotas, no queda ningún préstamo guardado")
    void testAtomicidad() {
        LocalDate hoy = LocalDate.now(clock);

        // Simulador de fallo en generador de cuotas
        CronogramaCalculator calculadorConFallo = new CronogramaCalculator() {
            @Override
            public List<CuotaDetalle> generarCronograma(BigDecimal capital, int plazoMeses, LocalDate fechaPrestamo, BigDecimal tea) {
                throw new RuntimeException("Fallo simulado e inesperado en la generación de cuotas");
            }
        };

        PrestamoService servicioConFallo = new PrestamoServiceImpl(
                prestamoRepository,
                cuotaRepository,
                clienteRepository,
                usuarioRepository,
                reniecClient,
                clienteRegistroHelper,
                clock,
                calculadorConFallo
        );

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000007")
                .nombreCliente("Lucía Fernanda Sánchez Chávez")
                .monto(new BigDecimal("2000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();

        assertThrows(RuntimeException.class, () -> servicioConFallo.registrarPrestamo(dto, "asesor.tumbes"));

        // Verificar que ningún préstamo quedó guardado en la base de datos
        List<Prestamo> prestamos = prestamoRepository.findAll();
        assertThat(prestamos).isEmpty();

        List<Cuota> cuotas = cuotaRepository.findAll();
        assertThat(cuotas).isEmpty();
    }

    @Test
    @DisplayName("Concurrencia: dos solicitudes simultáneas de 6000 para el mismo cliente -> exactamente una tiene éxito")
    void testConcurrenciaDosSolicitudesSimultaneas() throws Exception {
        LocalDate hoy = LocalDate.now(clock);

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000006")
                .nombreCliente("César Abraham Vallejo Mendoza")
                .monto(new BigDecimal("6000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();

        int numHilos = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numHilos);
        CountDownLatch readyLatch = new CountDownLatch(numHilos);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger exitos = new AtomicInteger(0);
        AtomicInteger fallos = new AtomicInteger(0);
        List<Throwable> excepciones = Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < numHilos; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    prestamoService.registrarPrestamo(dto, "asesor.trujillo");
                    exitos.incrementAndGet();
                } catch (Throwable t) {
                    fallos.incrementAndGet();
                    excepciones.add(t);
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Exactamente una tiene éxito y exactamente una falla por tope
        assertThat(exitos.get()).isEqualTo(1);
        assertThat(fallos.get()).isEqualTo(1);
        assertThat(excepciones.get(0)).isInstanceOf(NegocioException.class);
        assertThat(excepciones.get(0).getMessage()).contains("excede el tope de deuda");

        // En BD debe existir solo 1 préstamo por 6000
        Optional<Cliente> clienteOpt = clienteRepository.findByDni("10000006");
        assertThat(clienteOpt).isPresent();
        List<Prestamo> prestamos = prestamoRepository.findByClienteId(clienteOpt.get().getId());
        assertThat(prestamos).hasSize(1);
        assertThat(prestamos.get(0).getCapital()).isEqualByComparingTo(new BigDecimal("6000.00"));
    }

    @Autowired
    private com.example.pagaopiu.security.CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("Seguridad: GERENTE recibe 403; ASESOR de Trujillo registra préstamo con sucursal Trujillo")
    void testSeguridad() throws Exception {
        // 1. GERENTE intentando acceder a /prestamo/registrar recibe 403 Forbidden
        org.springframework.security.core.userdetails.UserDetails gerenteDetails = userDetailsService.loadUserByUsername("gerente");
        org.springframework.security.core.Authentication authGerente = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                gerenteDetails, "password123", gerenteDetails.getAuthorities());
        org.springframework.security.core.context.SecurityContext contextGerente = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        contextGerente.setAuthentication(authGerente);

        MockHttpSession sessionGerente = new MockHttpSession();
        sessionGerente.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contextGerente);

        mockMvc.perform(get("/prestamo/registrar").session(sessionGerente))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/prestamo/1").session(sessionGerente))
                .andExpect(status().isForbidden());

        // 2. ASESOR de Trujillo autenticado puede acceder a /prestamo/registrar
        org.springframework.security.core.userdetails.UserDetails asesorDetails = userDetailsService.loadUserByUsername("asesor.trujillo");
        org.springframework.security.core.Authentication authAsesor = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                asesorDetails, "password123", asesorDetails.getAuthorities());
        org.springframework.security.core.context.SecurityContext contextAsesor = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        contextAsesor.setAuthentication(authAsesor);

        MockHttpSession sessionAsesor = new MockHttpSession();
        sessionAsesor.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contextAsesor);

        mockMvc.perform(get("/prestamo/registrar").session(sessionAsesor))
                .andExpect(status().isOk());

        // 3. ASESOR de Trujillo registra un préstamo y la sucursal es "Trujillo"
        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni("10000008")
                .nombreCliente("Ángel Gabriel Peña Morales")
                .monto(new BigDecimal("1500.00"))
                .plazoMeses(6)
                .fechaPrestamo(LocalDate.now(clock))
                .build();

        Prestamo prestamo = prestamoService.registrarPrestamo(dto, "asesor.trujillo");
        assertThat(prestamo).isNotNull();
        assertThat(prestamo.getSucursal()).isNotNull();
        assertThat(prestamo.getSucursal().getNombre()).isEqualTo("Trujillo");
        assertThat(prestamo.getAsesor().getUsername()).isEqualTo("asesor.trujillo");
    }

    @Test
    @DisplayName("Rechaza registro de préstamo si el cliente es menor de 18 años")
    void testRechazaClienteMenorDeEdad() {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate fechaMenor = hoy.minusYears(17); // 17 años
        ReniecPadron padron = reniecClient.buscarPorDni("10000008").orElseThrow();

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(padron.getDni())
                .nombreCliente(padron.getNombreCompleto())
                .monto(new BigDecimal("1500.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .fechaNacimiento(fechaMenor)
                .build();

        NegocioException ex = assertThrows(NegocioException.class, () ->
                prestamoService.registrarPrestamo(dto, "asesor.trujillo"));

        assertThat(ex.getMessage()).contains("El cliente debe ser mayor de edad (mínimo 18 años)");
    }

    @Test
    @DisplayName("Acepta registro de préstamo si el cliente tiene 18 años o más")
    void testAceptaClienteMayorDeEdad() {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate fechaMayor = hoy.minusYears(18); // Exactamente 18 años
        ReniecPadron padron = reniecClient.buscarPorDni("10000008").orElseThrow();

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(padron.getDni())
                .nombreCliente(padron.getNombreCompleto())
                .monto(new BigDecimal("1500.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .fechaNacimiento(fechaMayor)
                .build();

        Prestamo prestamo = prestamoService.registrarPrestamo(dto, "asesor.trujillo");
        assertThat(prestamo).isNotNull();
        assertThat(prestamo.getId()).isNotNull();
    }

    @Test
    @DisplayName("Rechaza registro de préstamo si el cliente supera la edad máxima de 80 años")
    void testRechazaClienteMayorDe80Anos() {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate fecha81 = hoy.minusYears(81); // 81 años
        ReniecPadron padron = reniecClient.buscarPorDni("10000008").orElseThrow();

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(padron.getDni())
                .nombreCliente(padron.getNombreCompleto())
                .monto(new BigDecimal("1500.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .fechaNacimiento(fecha81)
                .build();

        NegocioException ex = assertThrows(NegocioException.class, () ->
                prestamoService.registrarPrestamo(dto, "asesor.trujillo"));

        assertThat(ex.getMessage()).contains("La edad máxima permitida para solicitar un préstamo es de 80 años");
    }

    @Test
    @DisplayName("Obtener préstamo por ID carga cliente, sucursal y asesor sin LazyInitializationException")
    void testObtenerPrestamoPorIdCargaRelaciones() {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate fechaMayor = hoy.minusYears(25);
        ReniecPadron padron = reniecClient.buscarPorDni("10000008").orElseThrow();

        PrestamoRegistroDTO dto = PrestamoRegistroDTO.builder()
                .dni(padron.getDni())
                .nombreCliente(padron.getNombreCompleto())
                .monto(new BigDecimal("1200.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .fechaNacimiento(fechaMayor)
                .build();

        Prestamo prestamo = prestamoService.registrarPrestamo(dto, "asesor.trujillo");
        assertThat(prestamo).isNotNull();

        Prestamo obtenido = prestamoService.obtenerPrestamoPorId(prestamo.getId());
        assertThat(obtenido).isNotNull();
        assertThat(obtenido.getCliente()).isNotNull();
        assertThat(obtenido.getCliente().getDni()).isEqualTo("10000008");
        assertThat(obtenido.getSucursal()).isNotNull();
        assertThat(obtenido.getSucursal().getNombre()).isNotEmpty();
        assertThat(obtenido.getAsesor()).isNotNull();
        assertThat(obtenido.getAsesor().getNombre()).isNotEmpty();
    }
}
