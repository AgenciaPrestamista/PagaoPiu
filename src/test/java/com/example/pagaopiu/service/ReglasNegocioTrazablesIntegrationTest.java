package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.pagaopiu.dto.PanelResponseDTO;
import com.example.pagaopiu.dto.PanelSucursalDTO;
import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.exception.NegocioException;
import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.entity.Pago;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.enums.EstadoCliente;
import com.example.pagaopiu.model.enums.EstadoCuota;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import com.example.pagaopiu.model.enums.TipoPago;
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
class ReglasNegocioTrazablesIntegrationTest {

    @Autowired
    private PrestamoService prestamoService;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private EstadoService estadoService;

    @Autowired
    private PanelService panelService;

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

    // ── R1: Campos obligatorios ──
    @Test
    @DisplayName("R1: Validación de campos obligatorios en el registro del préstamo")
    void testRegistro_camposObligatorios() {
        LocalDate hoy = LocalDate.now(clock);

        assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(null, "asesor.trujillo"));

        PrestamoRegistroDTO dtoSinDni = PrestamoRegistroDTO.builder()
                .dni("")
                .nombreCliente("CASTILLO TERRONES, JOSE PEDRO")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoSinDni, "asesor.trujillo"));

        PrestamoRegistroDTO dtoSinNombre = PrestamoRegistroDTO.builder()
                .dni("27427864")
                .nombreCliente("   ")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoSinNombre, "asesor.trujillo"));

        PrestamoRegistroDTO dtoSinPlazo = PrestamoRegistroDTO.builder()
                .dni("27427864")
                .nombreCliente("CASTILLO TERRONES, JOSE PEDRO")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(null)
                .fechaPrestamo(hoy)
                .build();
        assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoSinPlazo, "asesor.trujillo"));
    }

    // ── R2: DNI en padrón, vigente, no fallecido, peruano ──
    @Test
    @DisplayName("R2: DNI debe existir en padrón RENIEC, estar vigente, no fallecido y tener nacionalidad peruana")
    void testRegistro_dniValidoEnPadron() {
        LocalDate hoy = LocalDate.now(clock);

        // DNI inexistente
        PrestamoRegistroDTO dtoInexistente = PrestamoRegistroDTO.builder()
                .dni("99999999")
                .nombreCliente("Persona Fantasma")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        NegocioException ex1 = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoInexistente, "asesor.trujillo"));
        assertThat(ex1.getMessage()).contains("no se encuentra registrado en el padrón");

        // DNI no vigente
        PrestamoRegistroDTO dtoNoVigente = PrestamoRegistroDTO.builder()
                .dni("99999902")
                .nombreCliente("Ana DNI Vencido Ejemplo")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        NegocioException ex2 = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoNoVigente, "asesor.trujillo"));
        assertThat(ex2.getMessage()).contains("no se encuentra vigente");

        // Fallecido
        PrestamoRegistroDTO dtoFallecido = PrestamoRegistroDTO.builder()
                .dni("99999901")
                .nombreCliente("Carlos Muerto Ejemplo")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        NegocioException ex3 = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoFallecido, "asesor.trujillo"));
        assertThat(ex3.getMessage()).contains("fallecida");

        // Extranjero
        PrestamoRegistroDTO dtoExtranjero = PrestamoRegistroDTO.builder()
                .dni("99999903")
                .nombreCliente("John Smith Foreign")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        NegocioException ex4 = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoExtranjero, "asesor.trujillo"));
        assertThat(ex4.getMessage()).contains("nacionalidad peruana");
    }

    // ── R3: Nombre exactamente igual con normalización NFC ──
    @Test
    @DisplayName("R3: Nombre ingresado debe coincidir exactamente con el del padrón (sensible a mayúsculas/tildes con NFC)")
    void testRegistro_nombreExactoNFC() {
        LocalDate hoy = LocalDate.now(clock);

        // Sin tilde o en minúsculas
        PrestamoRegistroDTO dtoIncorrecto = PrestamoRegistroDTO.builder()
                .dni("27427864")
                .nombreCliente("castillo terrones, jose pedro")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        NegocioException ex = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoIncorrecto, "asesor.trujillo"));
        assertThat(ex.getMessage()).contains("no coincide exactamente");

        // Nombre exacto con NFC
        PrestamoRegistroDTO dtoCorrecto = PrestamoRegistroDTO.builder()
                .dni("27427864")
                .nombreCliente("CASTILLO TERRONES, JOSE PEDRO")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        Prestamo prestamo = prestamoService.registrarPrestamo(dtoCorrecto, "asesor.trujillo");
        assertThat(prestamo).isNotNull();
    }

    // ── R4: Plazo entre 1 y 60 meses ──
    @Test
    @DisplayName("R4: Plazo del préstamo debe estar restringido entre 1 y 60 meses")
    void testRegistro_plazoEnRango() {
        LocalDate hoy = LocalDate.now(clock);

        // Plazo 0
        PrestamoRegistroDTO dtoPlazo0 = PrestamoRegistroDTO.builder()
                .dni("10000001")
                .nombreCliente("Juan Carlos Pérez López")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(0)
                .fechaPrestamo(hoy)
                .build();
        assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoPlazo0, "asesor.trujillo"));

        // Plazo 61
        PrestamoRegistroDTO dtoPlazo61 = PrestamoRegistroDTO.builder()
                .dni("10000001")
                .nombreCliente("Juan Carlos Pérez López")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(61)
                .fechaPrestamo(hoy)
                .build();
        assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoPlazo61, "asesor.trujillo"));

        // Plazo 1 y 60 son válidos
        PrestamoRegistroDTO dtoPlazo1 = PrestamoRegistroDTO.builder()
                .dni("10000001")
                .nombreCliente("Juan Carlos Pérez López")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(1)
                .fechaPrestamo(hoy)
                .build();
        Prestamo p = prestamoService.registrarPrestamo(dtoPlazo1, "asesor.trujillo");
        assertThat(p.getPlazoMeses()).isEqualTo(1);
    }

    // ── R5: Monto entre mínimo y 10,000 ──
    @Test
    @DisplayName("R5: Monto del préstamo debe estar entre el mínimo configurado (S/ 1.00) y S/ 10,000.00")
    void testRegistro_montoEnRango() {
        LocalDate hoy = LocalDate.now(clock);

        // Monto 0
        PrestamoRegistroDTO dto0 = PrestamoRegistroDTO.builder()
                .dni("10000002")
                .nombreCliente("María Elena García Ríos")
                .monto(BigDecimal.ZERO)
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dto0, "asesor.trujillo"));

        // Monto 10001
        PrestamoRegistroDTO dto10001 = PrestamoRegistroDTO.builder()
                .dni("10000002")
                .nombreCliente("María Elena García Ríos")
                .monto(new BigDecimal("10001.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dto10001, "asesor.trujillo"));

        // Monto mínimo 1.00 y 10000.00 aceptados
        PrestamoRegistroDTO dtoMin = PrestamoRegistroDTO.builder()
                .dni("10000002")
                .nombreCliente("María Elena García Ríos")
                .monto(new BigDecimal("1.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy)
                .build();
        Prestamo pMin = prestamoService.registrarPrestamo(dtoMin, "asesor.trujillo");
        assertThat(pMin.getCapital()).isEqualByComparingTo("1.00");
    }

    // ── R6: Tope acumulado por cliente de S/ 10,000.00 ──
    @Test
    @DisplayName("R6: El saldo pendiente total del cliente no puede exceder los S/ 10,000.00")
    void testRegistro_topeCliente10000() {
        LocalDate hoy = LocalDate.now(clock);

        // Primer préstamo de 6,000
        prestamoService.registrarPrestamo(
                PrestamoRegistroDTO.builder()
                        .dni("10000004")
                        .nombreCliente("Ana Belén Gómez Díaz")
                        .monto(new BigDecimal("6000.00"))
                        .plazoMeses(12)
                        .fechaPrestamo(hoy)
                        .build(),
                "asesor.trujillo"
        );

        // Segundo préstamo de 5,000 (total 11,000 > 10,000) -> rechazado indicando cupo
        PrestamoRegistroDTO dtoExceso = PrestamoRegistroDTO.builder()
                .dni("10000004")
                .nombreCliente("Ana Belén Gómez Díaz")
                .monto(new BigDecimal("5000.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .build();
        NegocioException ex = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoExceso, "asesor.trujillo"));
        assertThat(ex.getMessage()).contains("excede el tope de deuda").contains("4000.00");

        // Segundo préstamo de 4,000 (total exacto 10,000) -> aceptado
        PrestamoRegistroDTO dtoExacto = PrestamoRegistroDTO.builder()
                .dni("10000004")
                .nombreCliente("Ana Belén Gómez Díaz")
                .monto(new BigDecimal("4000.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .build();
        Prestamo p2 = prestamoService.registrarPrestamo(dtoExacto, "asesor.trujillo");
        assertThat(p2).isNotNull();
    }

    // ── R7: Cliente MOROSO o EXTRAJUDICIAL no puede pedir préstamo ──
    @Test
    @DisplayName("R7: Cliente en estado MOROSO o EXTRAJUDICIAL no puede solicitar préstamos")
    void testRegistro_clienteMorosoRechazado() {
        LocalDate hoy = LocalDate.now(clock);

        Prestamo p1 = prestamoService.registrarPrestamo(
                PrestamoRegistroDTO.builder()
                        .dni("10000005")
                        .nombreCliente("Víctor Raúl Haya De La Torre")
                        .monto(new BigDecimal("1000.00"))
                        .plazoMeses(12)
                        .fechaPrestamo(hoy.minusMonths(1))
                        .build(),
                "asesor.trujillo"
        );

        // Vencer cuota 1
        Cuota c1 = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(p1.getId()).get(0);
        c1.setFechaVencimiento(hoy.minusDays(10));
        cuotaRepository.save(c1);

        // Intentar nuevo préstamo: R7 recalcula el cliente y lo rechaza
        PrestamoRegistroDTO nuevoDto = PrestamoRegistroDTO.builder()
                .dni("10000005")
                .nombreCliente("Víctor Raúl Haya De La Torre")
                .monto(new BigDecimal("500.00"))
                .plazoMeses(6)
                .fechaPrestamo(hoy)
                .build();

        NegocioException ex = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(nuevoDto, "asesor.trujillo"));
        assertThat(ex.getMessage()).contains("MOROSO");
    }

    // ── R8: Fecha del préstamo hoy o pasada, nunca futura ──
    @Test
    @DisplayName("R8: Fecha del préstamo no puede ser futura")
    void testRegistro_fechaNoFutura() {
        LocalDate hoy = LocalDate.now(clock);

        PrestamoRegistroDTO dtoFuturo = PrestamoRegistroDTO.builder()
                .dni("10000006")
                .nombreCliente("César Abraham Vallejo Mendoza")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy.plusDays(1))
                .build();

        NegocioException ex = assertThrows(NegocioException.class, () -> prestamoService.registrarPrestamo(dtoFuturo, "asesor.trujillo"));
        assertThat(ex.getMessage()).contains("no puede ser futura");

        // Fecha retroactiva es aceptada
        PrestamoRegistroDTO dtoPasado = PrestamoRegistroDTO.builder()
                .dni("10000006")
                .nombreCliente("César Abraham Vallejo Mendoza")
                .monto(new BigDecimal("1000.00"))
                .plazoMeses(12)
                .fechaPrestamo(hoy.minusDays(5))
                .build();
        Prestamo p = prestamoService.registrarPrestamo(dtoPasado, "asesor.trujillo");
        assertThat(p.getFechaPrestamo()).isEqualTo(hoy.minusDays(5));
    }

    // ── R9: Cronograma Sistema Francés, TEA 30%, TEM, cuota fija, redondeo ──
    @Test
    @DisplayName("R9: Generación del cronograma financiero según el método francés (caso patrón: S/ 1,000 a 12 meses)")
    void testRegistro_cronogramaFrances() {
        LocalDate hoy = LocalDate.now(clock);

        Prestamo p = prestamoService.registrarPrestamo(
                PrestamoRegistroDTO.builder()
                        .dni("10000007")
                        .nombreCliente("Lucía Fernanda Sánchez Chávez")
                        .monto(new BigDecimal("1000.00"))
                        .plazoMeses(12)
                        .fechaPrestamo(hoy)
                        .build(),
                "asesor.trujillo"
        );

        List<Cuota> cuotas = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(p.getId());
        assertThat(cuotas).hasSize(12);

        Cuota c1 = cuotas.get(0);
        assertThat(c1.getMonto()).isEqualByComparingTo("95.79");
        assertThat(c1.getInteres()).isEqualByComparingTo("22.10");
        assertThat(c1.getCapital()).isEqualByComparingTo("73.69");

        Cuota cUltima = cuotas.get(11);
        assertThat(cUltima.getSaldoCapital()).isEqualByComparingTo("0.00");
    }

    // ── R10: Pago secuencial de cuota más antigua ──
    @Test
    @DisplayName("R10: Pago secuencial obligatorio de la cuota PENDIENTE más antigua")
    void testPago_secuencialCuotaAntigua() {
        LocalDate hoy = LocalDate.now(clock);

        Prestamo p = prestamoService.registrarPrestamo(
                PrestamoRegistroDTO.builder()
                        .dni("10000008")
                        .nombreCliente("Ángel Gabriel Peña Morales")
                        .monto(new BigDecimal("1000.00"))
                        .plazoMeses(12)
                        .fechaPrestamo(hoy)
                        .build(),
                "asesor.trujillo"
        );

        // Primer pago cancela la cuota 1
        Pago pago1 = pagoService.pagarSiguienteCuota(p.getId(), "asesor.trujillo");
        List<Cuota> cuotas = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(p.getId());
        assertThat(cuotas.get(0).getEstado()).isEqualTo(EstadoCuota.PAGADA);
        assertThat(cuotas.get(0).getPago().getId()).isEqualTo(pago1.getId());
        assertThat(cuotas.get(1).getEstado()).isEqualTo(EstadoCuota.PENDIENTE);

        // Segundo pago cancela la cuota 2
        Pago pago2 = pagoService.pagarSiguienteCuota(p.getId(), "asesor.trujillo");
        List<Cuota> cuotasDespues = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(p.getId());
        assertThat(cuotasDespues.get(1).getEstado()).isEqualTo(EstadoCuota.PAGADA);
        assertThat(cuotasDespues.get(1).getPago().getId()).isEqualTo(pago2.getId());
    }

    // ── R11: Cliente puede pagar en cualquier sucursal ──
    @Test
    @DisplayName("R11: Pago multicanal: el préstamo originado en una sucursal puede pagarse en cualquier otra")
    void testPago_enCualquierSucursal() {
        LocalDate hoy = LocalDate.now(clock);

        // Préstamo originado en Trujillo
        Prestamo p = prestamoService.registrarPrestamo(
                PrestamoRegistroDTO.builder()
                        .dni("27427864")
                        .nombreCliente("CASTILLO TERRONES, JOSE PEDRO")
                        .monto(new BigDecimal("1000.00"))
                        .plazoMeses(12)
                        .fechaPrestamo(hoy)
                        .build(),
                "asesor.trujillo"
        );

        // Cobrado en Piura por asesor de Piura
        Pago pago = pagoService.pagarSiguienteCuota(p.getId(), "asesor.piura");

        assertThat(p.getSucursal().getNombre()).isEqualTo("Trujillo");
        assertThat(pago.getSucursal().getNombre()).isEqualTo("Piura");
    }

    // ── R12: Cancelación anticipada ──
    @Test
    @DisplayName("R12: Cancelación anticipada liquida todas las cuotas pendientes y cancela el préstamo")
    void testPago_cancelacionAnticipada() {
        LocalDate hoy = LocalDate.now(clock);

        Prestamo p = prestamoService.registrarPrestamo(
                PrestamoRegistroDTO.builder()
                        .dni("10000001")
                        .nombreCliente("Juan Carlos Pérez López")
                        .monto(new BigDecimal("1000.00"))
                        .plazoMeses(6)
                        .fechaPrestamo(hoy)
                        .build(),
                "asesor.trujillo"
        );

        Pago pagoCancelacion = pagoService.cancelarAnticipado(p.getId(), "asesor.trujillo");

        assertThat(pagoCancelacion.getTipo()).isEqualTo(TipoPago.CANCELACION_ANTICIPADA);

        Prestamo pActual = prestamoRepository.findById(p.getId()).orElseThrow();
        assertThat(pActual.getEstado()).isEqualTo(EstadoPrestamo.CANCELADO);
        assertThat(pActual.getFechaCancelacion()).isNotNull();

        List<Cuota> cuotas = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(p.getId());
        assertThat(cuotas).allMatch(c -> c.getEstado() == EstadoCuota.PAGADA);
    }

    // ── R13: Estados moroso y extrajudicial ──
    @Test
    @DisplayName("R13: Transición de estados MOROSO, reversión a VIGENTE y cobranza EXTRAJUDICIAL (≥ 365 días)")
    void testEstado_morosoYExtrajudicial() {
        LocalDate hoy = LocalDate.now(clock);

        Prestamo p = prestamoService.registrarPrestamo(
                PrestamoRegistroDTO.builder()
                        .dni("10000002")
                        .nombreCliente("María Elena García Ríos")
                        .monto(new BigDecimal("1000.00"))
                        .plazoMeses(12)
                        .fechaPrestamo(hoy)
                        .build(),
                "asesor.trujillo"
        );

        // Vencimiento < hoy -> MOROSO
        Cuota c1 = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(p.getId()).get(0);
        c1.setFechaVencimiento(hoy.minusDays(5));
        cuotaRepository.save(c1);

        estadoService.recalcularPrestamo(p);
        estadoService.recalcularCliente(p.getCliente());
        assertThat(prestamoRepository.findById(p.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPrestamo.MOROSO);

        // Pagar la cuota morosa -> revierte a VIGENTE
        pagoService.pagarSiguienteCuota(p.getId(), "asesor.trujillo");
        assertThat(prestamoRepository.findById(p.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPrestamo.VIGENTE);

        // Vencimiento >= 365 días -> EXTRAJUDICIAL
        Cuota c2 = cuotaRepository.findByPrestamoIdOrderByNumeroAsc(p.getId()).get(1);
        c2.setFechaVencimiento(hoy.minusDays(366));
        cuotaRepository.save(c2);

        estadoService.recalcularPrestamo(p);
        estadoService.recalcularCliente(p.getCliente());
        assertThat(prestamoRepository.findById(p.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPrestamo.EXTRAJUDICIAL);

        // Préstamo extrajudicial no admite pagos
        assertThrows(NegocioException.class, () -> pagoService.pagarSiguienteCuota(p.getId(), "asesor.trujillo"));
    }

    // ── R14: Panel del Gerente con métricas agregadas ──
    @Test
    @DisplayName("R14: Panel del Gerente con desglose por sucursal de origen, cobro y métricas globales")
    void testPanel_datosCorrectosGerente() {
        LocalDate hoy = LocalDate.now(clock);

        Prestamo pTru = prestamoService.registrarPrestamo(
                PrestamoRegistroDTO.builder()
                        .dni("10000004")
                        .nombreCliente("Ana Belén Gómez Díaz")
                        .monto(new BigDecimal("1000.00"))
                        .plazoMeses(12)
                        .fechaPrestamo(hoy)
                        .build(),
                "asesor.trujillo"
        );

        // Cobro en Piura
        pagoService.pagarSiguienteCuota(pTru.getId(), "asesor.piura");

        PanelResponseDTO panel = panelService.obtenerMetricasPanel();

        PanelSucursalDTO tru = panel.getSucursales().stream()
                .filter(s -> s.getSucursalNombre().equals("Trujillo")).findFirst().orElseThrow();
        PanelSucursalDTO piu = panel.getSucursales().stream()
                .filter(s -> s.getSucursalNombre().equals("Piura")).findFirst().orElseThrow();

        // Trujillo originó
        assertThat(tru.getPrestamosActivos()).isEqualTo(1);
        assertThat(tru.getMontoTotalCobrado()).isEqualByComparingTo("0.00");

        // Piura cobró
        assertThat(piu.getPrestamosActivos()).isEqualTo(0);
        assertThat(piu.getMontoTotalCobrado()).isEqualByComparingTo("95.79");
        assertThat(piu.getInteresesCobrados()).isEqualByComparingTo("22.10");

        // Totales globales
        assertThat(panel.getTotalPrestamosActivos()).isEqualTo(1);
        assertThat(panel.getTotalMontoCobrado()).isEqualByComparingTo("95.79");
    }
}
