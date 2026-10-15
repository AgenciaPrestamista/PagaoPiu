package com.example.pagaopiu.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CuotaDetalle(
    int numero,
    LocalDate fechaVencimiento,
    BigDecimal capital,
    BigDecimal interes,
    BigDecimal monto,
    BigDecimal saldoCapital
) {}
