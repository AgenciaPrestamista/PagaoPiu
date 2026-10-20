package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.exception.NegocioException;
import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.enums.EstadoCliente;
import com.example.pagaopiu.model.enums.EstadoCuota;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import com.example.pagaopiu.repository.ClienteRepository;
import com.example.pagaopiu.repository.CuotaRepository;
import com.example.pagaopiu.repository.PagoRepository;
import com.example.pagaopiu.repository.PrestamoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EstadoIntegrationTest {

    @Autowired
    private EstadoService estadoService;

    @Autowired
    private PrestamoService prestamoService;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private PrestamoRepository prestamoRepository;

    @Autowired
    private CuotaRepository cuotaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private com.example.pagaopiu.TestDatabaseCleaner databaseCleaner;

    @BeforeEach
    void limpiarBaseDatos() {
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
    @DisplayName("Crédito nuevo con cuotas al día: estado VIGENTE y cliente ACTIVO")
    void testCreditoAlDiaPermaneceVigente() {
        Prestamo prestamo = crearPrestamoDemo("27427864", "CASTILLO TERRONES, JOSE PEDRO", new BigDecimal("1000.00"), 12, "asesor.trujillo");

        estadoService.recalcularPrestamo(prestamo);
        estadoService.recalcularCliente(prestamo.getCliente());

        Prestamo pActual = prestamoRepository.findById(prestamo.getId()).orElseThrow();
        Cliente cActual = clienteRepository.findById(prestamo.getCliente().getId()).orElseThrow();

        assertThat(pActual.getEstado()).isEqualTo(EstadoPrestamo.VIGENTE);
        assertThat(cActual.getEstado()).isEqualTo(EstadoCliente.ACTIVO);
    }

    @Test
    @DisplayName("Cuota vencida (< hoy): préstamo pasa a MOROSO y cliente pasa a MOROSO")
    void testCuotaVencidaPasaAMoroso() {
        Prestamo prestamo = crearPrestamoDemo("10000001", "Juan Carlos Pérez López", new BigDecimal("1000.00"), 12, "asesor.trujillo");

        // Simular que la cuota 1 venció hace 10 días
        LocalDate hoy = LocalDate.now(clock);
        List<Cuota> cuotas = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamo.getId());
        Cuota c1 = cuotas.get(0);
        c1.setFechaVencimiento(hoy.minusDays(10));
        cuotaRepository.save(c1);

        estadoService.recalcularPrestamo(prestamo);
        estadoService.recalcularCliente(prestamo.getCliente());

        Prestamo pActual = prestamoRepository.findById(prestamo.getId()).orElseThrow();
        Cliente cActual = clienteRepository.findById(prestamo.getCliente().getId()).orElseThrow();

        assertThat(pActual.getEstado()).isEqualTo(EstadoPrestamo.MOROSO);
        assertThat(cActual.getEstado()).isEqualTo(EstadoCliente.MOROSO);
    }

    @Test
    @DisplayName("Al pagar la cuota vencida: préstamo vuelve a VIGENTE y cliente vuelve a ACTIVO")
    void testPagoCuotaVencidaRevierteAVigente() {
        Prestamo prestamo = crearPrestamoDemo("10000002", "María Elena García Ríos", new BigDecimal("1000.00"), 12, "asesor.trujillo");

        LocalDate hoy = LocalDate.now(clock);
        List<Cuota> cuotas = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamo.getId());
        Cuota c1 = cuotas.get(0);
        c1.setFechaVencimiento(hoy.minusDays(5));
        cuotaRepository.save(c1);

        estadoService.recalcularPrestamo(prestamo);
        estadoService.recalcularCliente(prestamo.getCliente());
        assertThat(prestamoRepository.findById(prestamo.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPrestamo.MOROSO);

        // Se paga la cuota 1 vencida
        pagoService.pagarSiguienteCuota(prestamo.getId(), "asesor.trujillo");

        Prestamo pDespues = prestamoRepository.findById(prestamo.getId()).orElseThrow();
        Cliente cDespues = clienteRepository.findById(prestamo.getCliente().getId()).orElseThrow();

        // Al no quedar más cuotas vencidas, el préstamo revierte a VIGENTE y el cliente a ACTIVO
        assertThat(pDespues.getEstado()).isEqualTo(EstadoPrestamo.VIGENTE);
        assertThat(cDespues.getEstado()).isEqualTo(EstadoCliente.ACTIVO);
    }

    @Test
    @DisplayName("Atraso >= 365 días: pasa a EXTRAJUDICIAL y NO revierte automáticamente")
    void testAtraso365DiasPasaAExtrajudicialYNoRevierte() {
        Prestamo prestamo = crearPrestamoDemo("10000004", "Ana Belén Gómez Díaz", new BigDecimal("1000.00"), 12, "asesor.piura");

        LocalDate hoy = LocalDate.now(clock);
        List<Cuota> cuotas = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamo.getId());
        Cuota c1 = cuotas.get(0);
        c1.setFechaVencimiento(hoy.minusDays(366));
        cuotaRepository.save(c1);

        estadoService.recalcularPrestamo(prestamo);
        estadoService.recalcularCliente(prestamo.getCliente());

        Prestamo pExtra = prestamoRepository.findById(prestamo.getId()).orElseThrow();
        Cliente cExtra = clienteRepository.findById(prestamo.getCliente().getId()).orElseThrow();

        assertThat(pExtra.getEstado()).isEqualTo(EstadoPrestamo.EXTRAJUDICIAL);
        assertThat(cExtra.getEstado()).isEqualTo(EstadoCliente.EXTRAJUDICIAL);

        // EXTRAJUDICIAL no revierte solo tras nuevos recálculos
        estadoService.recalcularPrestamo(pExtra);
        estadoService.recalcularCliente(cExtra);

        assertThat(prestamoRepository.findById(prestamo.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPrestamo.EXTRAJUDICIAL);
        assertThat(clienteRepository.findById(cExtra.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCliente.EXTRAJUDICIAL);
    }

    @Test
    @DisplayName("Préstamo en estado EXTRAJUDICIAL no admite pagos")
    void testExtrajudicialNoAdmitePagos() {
        Prestamo prestamo = crearPrestamoDemo("10000005", "Víctor Raúl Haya De La Torre", new BigDecimal("500.00"), 6, "asesor.trujillo");
        prestamo.setEstado(EstadoPrestamo.EXTRAJUDICIAL);
        prestamoRepository.save(prestamo);

        NegocioException ex = assertThrows(NegocioException.class,
                () -> pagoService.pagarSiguienteCuota(prestamo.getId(), "asesor.trujillo"));
        assertThat(ex.getMessage()).contains("EXTRAJUDICIAL y no admite pagos");
    }

    @Test
    @DisplayName("R7 conectado: cliente con cuota vencida es recalculado como MOROSO y se rechaza un nuevo crédito")
    void testR7RecalculaClienteYRechazaNuevoCredito() {
        Prestamo prestamo = crearPrestamoDemo("10000006", "César Abraham Vallejo Mendoza", new BigDecimal("1000.00"), 12, "asesor.trujillo");

        // Cuota vence
        LocalDate hoy = LocalDate.now(clock);
        Cuota c1 = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(prestamo.getId()).get(0);
        c1.setFechaVencimiento(hoy.minusDays(15));
        cuotaRepository.save(c1);

        // Sin haber ejecutado recalcularTodos, el nuevo registro debe invocar recalcularCliente en R7
        PrestamoRegistroDTO nuevoDTO = PrestamoRegistroDTO.builder()
                .dni("10000006")
                .nombreCliente("César Abraham Vallejo Mendoza")
                .monto(new BigDecimal("500.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .build();

        NegocioException ex = assertThrows(NegocioException.class,
                () -> prestamoService.registrarPrestamo(nuevoDTO, "asesor.trujillo"));

        assertThat(ex.getMessage()).contains("MOROSO");

        // Al invocar recalcularCliente directamente, se persiste el estado MOROSO
        estadoService.recalcularCliente(prestamo.getCliente());
        assertThat(clienteRepository.findByDni("10000006").orElseThrow().getEstado()).isEqualTo(EstadoCliente.MOROSO);
    }

    @Test
    @DisplayName("Idempotencia: recalcularTodos() genera el mismo resultado al ejecutarse múltiples veces")
    void testIdempotenciaRecalcularTodos() {
        Prestamo p1 = crearPrestamoDemo("10000007", "Lucía Fernanda Sánchez Chávez", new BigDecimal("1000.00"), 12, "asesor.trujillo");
        Prestamo p2 = crearPrestamoDemo("10000008", "Ángel Gabriel Peña Morales", new BigDecimal("1000.00"), 12, "asesor.trujillo");

        LocalDate hoy = LocalDate.now(clock);
        // p1 moroso
        Cuota cP1 = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(p1.getId()).get(0);
        cP1.setFechaVencimiento(hoy.minusDays(5));
        cuotaRepository.save(cP1);

        // p2 extrajudicial
        Cuota cP2 = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(p2.getId()).get(0);
        cP2.setFechaVencimiento(hoy.minusDays(370));
        cuotaRepository.save(cP2);

        // Primera ejecución
        estadoService.recalcularTodos();

        EstadoPrestamo estP1_1 = prestamoRepository.findById(p1.getId()).orElseThrow().getEstado();
        EstadoPrestamo estP2_1 = prestamoRepository.findById(p2.getId()).orElseThrow().getEstado();
        EstadoCliente estCP1_1 = clienteRepository.findById(p1.getCliente().getId()).orElseThrow().getEstado();
        EstadoCliente estCP2_1 = clienteRepository.findById(p2.getCliente().getId()).orElseThrow().getEstado();

        assertThat(estP1_1).isEqualTo(EstadoPrestamo.MOROSO);
        assertThat(estP2_1).isEqualTo(EstadoPrestamo.EXTRAJUDICIAL);
        assertThat(estCP1_1).isEqualTo(EstadoCliente.MOROSO);
        assertThat(estCP2_1).isEqualTo(EstadoCliente.EXTRAJUDICIAL);

        // Segunda ejecución (idempotencia)
        estadoService.recalcularTodos();

        EstadoPrestamo estP1_2 = prestamoRepository.findById(p1.getId()).orElseThrow().getEstado();
        EstadoPrestamo estP2_2 = prestamoRepository.findById(p2.getId()).orElseThrow().getEstado();
        EstadoCliente estCP1_2 = clienteRepository.findById(p1.getCliente().getId()).orElseThrow().getEstado();
        EstadoCliente estCP2_2 = clienteRepository.findById(p2.getCliente().getId()).orElseThrow().getEstado();

        assertThat(estP1_2).isEqualTo(estP1_1);
        assertThat(estP2_2).isEqualTo(estP2_1);
        assertThat(estCP1_2).isEqualTo(estCP1_1);
        assertThat(estCP2_2).isEqualTo(estCP2_1);
    }
}
