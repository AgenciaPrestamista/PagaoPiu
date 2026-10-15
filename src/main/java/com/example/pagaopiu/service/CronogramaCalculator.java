package com.example.pagaopiu.service;

import com.example.pagaopiu.dto.CuotaDetalle;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CronogramaCalculator {

    public static final BigDecimal TEA_DEFECTO = new BigDecimal("0.30");
    private static final MathContext MC = MathContext.DECIMAL128;

    /**
     * Calcula la Tasa Efectiva Mensual (TEM) a partir de la TEA.
     * TEM = (1 + TEA)^(30/360) - 1, redondeada a 10 decimales con HALF_UP.
     */
    public BigDecimal calcularTem(BigDecimal tea) {
        double teaDouble = tea.doubleValue();
        double temDouble = Math.pow(1.0 + teaDouble, 30.0 / 360.0) - 1.0;
        return BigDecimal.valueOf(temDouble).setScale(10, RoundingMode.HALF_UP);
    }

    /**
     * Calcula la cuota fija mensual bajo el sistema francés usando BigDecimal:
     * factor = (1 + tem)^plazoMeses
     * cuota = (capital * tem * factor) / (factor - 1)
     */
    public BigDecimal calcularCuotaFija(BigDecimal capital, BigDecimal tem, int plazoMeses) {
        BigDecimal factor = BigDecimal.ONE.add(tem).pow(plazoMeses, MC);
        BigDecimal cuota = capital.multiply(tem)
                .multiply(factor)
                .divide(factor.subtract(BigDecimal.ONE), MC);
        return cuota.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Genera el cronograma de cuotas francés completo.
     * Vencimiento de cuota k = fechaPrestamo + 30*k días.
     * Redondeo a 2 decimales HALF_UP; la última cuota absorbe el residuo del saldo de capital.
     */
    public List<CuotaDetalle> generarCronograma(BigDecimal capital, int plazoMeses, LocalDate fechaPrestamo, BigDecimal tea) {
        BigDecimal tem = calcularTem(tea);
        BigDecimal cuotaFija = calcularCuotaFija(capital, tem, plazoMeses);
        BigDecimal saldo = capital.setScale(2, RoundingMode.HALF_UP);

        List<CuotaDetalle> cronograma = new ArrayList<>(plazoMeses);

        for (int k = 1; k <= plazoMeses; k++) {
            LocalDate fechaVencimiento = fechaPrestamo.plusDays(30L * k);
            BigDecimal interes = saldo.multiply(tem).setScale(2, RoundingMode.HALF_UP);
            BigDecimal amortizacionCapital;
            BigDecimal montoCuota;

            if (k == plazoMeses) {
                amortizacionCapital = saldo;
                montoCuota = amortizacionCapital.add(interes).setScale(2, RoundingMode.HALF_UP);
                saldo = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            } else {
                amortizacionCapital = cuotaFija.subtract(interes).setScale(2, RoundingMode.HALF_UP);
                montoCuota = cuotaFija;
                saldo = saldo.subtract(amortizacionCapital).setScale(2, RoundingMode.HALF_UP);
            }

            cronograma.add(new CuotaDetalle(k, fechaVencimiento, amortizacionCapital, interes, montoCuota, saldo));
        }

        return cronograma;
    }
}
