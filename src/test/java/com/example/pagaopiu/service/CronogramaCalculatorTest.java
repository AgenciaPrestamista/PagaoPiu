package com.example.pagaopiu.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pagaopiu.dto.CuotaDetalle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CronogramaCalculatorTest {

    private final CronogramaCalculator calculator = new CronogramaCalculator();
    private final LocalDate fechaBase = LocalDate.of(2026, 10, 13);
    private final BigDecimal tea = CronogramaCalculator.TEA_DEFECTO;

    private void validarReglasGenerales(List<CuotaDetalle> cronograma, BigDecimal capitalOriginal) {
        // 1. La suma de los capitales amortizados es igual al capital prestado
        BigDecimal sumaCapital = cronograma.stream()
                .map(CuotaDetalle::capital)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumaCapital).isEqualByComparingTo(capitalOriginal);

        // 2. El saldo final es 0.00
        assertThat(cronograma.get(cronograma.size() - 1).saldoCapital())
                .isEqualByComparingTo(BigDecimal.ZERO);

        // 3. Ninguna amortización es negativa
        for (CuotaDetalle cuota : cronograma) {
            assertThat(cuota.capital()).isGreaterThan(BigDecimal.ZERO);
            assertThat(cuota.interes()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(cuota.monto()).isEqualByComparingTo(cuota.capital().add(cuota.interes()));
        }

        // 4. Los saldos son estrictamente decrecientes
        BigDecimal saldoAnterior = capitalOriginal;
        for (CuotaDetalle cuota : cronograma) {
            assertThat(cuota.saldoCapital()).isLessThan(saldoAnterior);
            saldoAnterior = cuota.saldoCapital();
        }
    }

    @Test
    @DisplayName("Caso (a): 1000 soles a 12 meses -> cuota 95.79, interes C1 22.10, capital C1 73.69, saldo final 0.00")
    void testCasoA_1000a12Meses() {
        BigDecimal capital = new BigDecimal("1000.00");
        int plazo = 12;

        List<CuotaDetalle> cronograma = calculator.generarCronograma(capital, plazo, fechaBase, tea);

        assertThat(cronograma).hasSize(plazo);
        validarReglasGenerales(cronograma, capital);

        // Aserciones específicas del caso (a)
        CuotaDetalle cuota1 = cronograma.get(0);
        assertThat(cuota1.monto()).isEqualByComparingTo(new BigDecimal("95.79"));
        assertThat(cuota1.interes()).isEqualByComparingTo(new BigDecimal("22.10"));
        assertThat(cuota1.capital()).isEqualByComparingTo(new BigDecimal("73.69"));

        // Aserción sobre fecha de vencimiento de la cuota 1: 2026-11-12
        assertThat(cuota1.fechaVencimiento()).isEqualTo(LocalDate.of(2026, 11, 12));

        // Aserción para cada cuota k (1..12): fechaVencimiento == fechaBase.plusDays(30L * k)
        for (int k = 1; k <= plazo; k++) {
            CuotaDetalle cuota = cronograma.get(k - 1);
            assertThat(cuota.fechaVencimiento()).isEqualTo(fechaBase.plusDays(30L * k));
        }
    }

    @Test
    @DisplayName("Caso (b): 1 sol a 1 mes -> cuota 1.02, interes C1 0.02, capital C1 1.00, saldo final 0.00")
    void testCasoB_1SolA1Mes() {
        BigDecimal capital = new BigDecimal("1.00");
        int plazo = 1;

        List<CuotaDetalle> cronograma = calculator.generarCronograma(capital, plazo, fechaBase, tea);

        assertThat(cronograma).hasSize(plazo);
        validarReglasGenerales(cronograma, capital);

        CuotaDetalle cuota1 = cronograma.get(0);
        assertThat(cuota1.monto()).isEqualByComparingTo(new BigDecimal("1.02"));
        assertThat(cuota1.interes()).isEqualByComparingTo(new BigDecimal("0.02"));
        assertThat(cuota1.capital()).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    @Test
    @DisplayName("Caso (c): 10000 soles a 60 meses -> diferencia entre ultima cuota y cuota fija <= 1.00")
    void testCasoC_10000a60Meses() {
        BigDecimal capital = new BigDecimal("10000.00");
        int plazo = 60;

        BigDecimal tem = calculator.calcularTem(tea);
        BigDecimal cuotaFija = calculator.calcularCuotaFija(capital, tem, plazo);

        List<CuotaDetalle> cronograma = calculator.generarCronograma(capital, plazo, fechaBase, tea);

        assertThat(cronograma).hasSize(plazo);
        validarReglasGenerales(cronograma, capital);

        CuotaDetalle ultimaCuota = cronograma.get(plazo - 1);
        BigDecimal diferencia = ultimaCuota.monto().subtract(cuotaFija).abs();
        assertThat(diferencia).isLessThanOrEqualTo(new BigDecimal("1.00"));
    }
}
