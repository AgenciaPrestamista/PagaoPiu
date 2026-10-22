package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.pagaopiu.dto.CuotaDetalle;
import com.example.pagaopiu.dto.PrestamoRegistroDTO;
import com.example.pagaopiu.exception.NegocioException;
import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
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
class TopeClienteMultiSedeIntegrationTest {

    private static final String DNI = "10000004";
    private static final String NOMBRE = "Ana Belén Gómez Díaz";

    @Autowired
    private PrestamoService prestamoService;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private PrestamoRepository prestamoRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private com.example.pagaopiu.TestDatabaseCleaner databaseCleaner;

    @BeforeEach
    void limpiar() {
        databaseCleaner.clean();
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    private PrestamoRegistroDTO dto(String doc, String nombre, String monto, int plazo) {
        return PrestamoRegistroDTO.builder()
                .dni(doc)
                .nombreCliente(nombre)
                .monto(new BigDecimal(monto))
                .plazoMeses(plazo)
                .fechaPrestamo(LocalDate.now(clock))
                .build();
    }

    @Test
    @DisplayName("5000 (Trujillo) + 5000 (Piura) + 1 (Chiclayo): el tercero debe rechazarse")
    void tresSedesDistintas_terceroRechazado() {
        prestamoService.registrarPrestamo(dto(DNI, NOMBRE, "5000.00", 12), "asesor.trujillo");
        prestamoService.registrarPrestamo(dto(DNI, NOMBRE, "5000.00", 12), "asesor.piura");

        NegocioException ex = assertThrows(NegocioException.class,
                () -> prestamoService.registrarPrestamo(dto(DNI, NOMBRE, "1.00", 1), "asesor.chiclayo"));
        assertThat(ex.getMessage()).contains("excede el tope de deuda").contains("Cupo restante disponible: S/ 0.00");
    }

    @Test
    @DisplayName("Flujo contrato (EN_ESPERA -> VIGENTE) en sedes distintas: el 1 sol tambien se rechaza")
    void flujoContrato_sedesDistintas() {
        Prestamo a = prestamoService.generarContrato(dto(DNI, NOMBRE, "5000.00", 12), "asesor.trujillo");
        a.setEstado(EstadoPrestamo.VIGENTE);
        prestamoRepository.save(a);
        Prestamo b = prestamoService.generarContrato(dto(DNI, NOMBRE, "5000.00", 12), "asesor.piura");
        b.setEstado(EstadoPrestamo.VIGENTE);
        prestamoRepository.save(b);

        NegocioException ex = assertThrows(NegocioException.class,
                () -> prestamoService.generarContrato(dto(DNI, NOMBRE, "1.00", 1), "asesor.chimbote"));
        assertThat(ex.getMessage()).contains("excede el tope de deuda");
    }

    @Test
    @DisplayName("Pagar cuotas sueltas NO libera cupo: el 1 sol se rechaza")
    void conCuotaPagada_unSolRechazado() {
        Prestamo a = prestamoService.registrarPrestamo(dto(DNI, NOMBRE, "5000.00", 12), "asesor.trujillo");
        prestamoService.registrarPrestamo(dto(DNI, NOMBRE, "5000.00", 12), "asesor.piura");
        pagoService.pagarSiguienteCuota(a.getId(), "asesor.trujillo");
        pagoService.pagarSiguienteCuota(a.getId(), "asesor.trujillo");

        NegocioException ex = assertThrows(NegocioException.class,
                () -> prestamoService.registrarPrestamo(dto(DNI, NOMBRE, "1.00", 1), "asesor.chiclayo"));
        assertThat(ex.getMessage()).contains("excede el tope de deuda");
    }

    @Test
    @DisplayName("Cancelar un prestamo completo SI libera cupo: 5000 cancelado + 5000 vigente + 1 entra; luego otro 5000 se rechaza")
    void cancelacionTotal_liberaCupo() {
        Prestamo a = prestamoService.registrarPrestamo(dto(DNI, NOMBRE, "5000.00", 12), "asesor.piura");
        pagoService.cancelarAnticipado(a.getId(), "asesor.piura");
        prestamoService.registrarPrestamo(dto(DNI, NOMBRE, "5000.00", 12), "asesor.trujillo");
        prestamoService.registrarPrestamo(dto(DNI, NOMBRE, "1.00", 1), "asesor.piura");

        NegocioException ex = assertThrows(NegocioException.class,
                () -> prestamoService.registrarPrestamo(dto(DNI, NOMBRE, "5000.00", 12), "asesor.chimbote"));
        assertThat(ex.getMessage()).contains("excede el tope de deuda").contains("4999.00");
    }

    @Test
    @DisplayName("Persona con DNI y luego con su RUC 10: hoy son clientes distintos (cada uno con su propio tope)")
    void dniYRuc10_mismoTitular() {
        prestamoService.registrarPrestamo(dto(DNI, NOMBRE, "10000.00", 12), "asesor.trujillo");
        String ruc10 = "10" + DNI + "5";

        // Si esto NO lanza, el tope se esta eludiendo usando el RUC 10 de la misma persona.
        NegocioException ex = null;
        try {
            prestamoService.registrarPrestamo(dto(ruc10, NOMBRE, "1.00", 1), "asesor.piura");
        } catch (NegocioException e) {
            ex = e;
        }
        System.out.println(">>> DNI+RUC10 -> " + (ex == null ? "ACEPTADO (hueco)" : "rechazado: " + ex.getMessage()));
        assertThat(ex).as("el RUC 10 derivado del DNI debe contar contra el mismo tope").isNotNull();
    }

    @Test
    @DisplayName("La suma del capital de las cuotas siempre iguala el monto prestado (plazos 1..60, montos chicos y grandes)")
    void sumaCapitalCuotas_igualaMonto() {
        CronogramaCalculator calc = new CronogramaCalculator();
        for (String monto : List.of("1.00", "5000.00", "4999.99", "9999.99", "10000.00")) {
            for (int plazo = 1; plazo <= 60; plazo++) {
                List<CuotaDetalle> cr = calc.generarCronograma(new BigDecimal(monto), plazo, LocalDate.now(clock), new BigDecimal("0.30"));
                BigDecimal suma = cr.stream().map(CuotaDetalle::capital).reduce(BigDecimal.ZERO, BigDecimal::add);
                assertThat(suma).as("monto=%s plazo=%d", monto, plazo).isEqualByComparingTo(monto);
            }
        }
    }
}
