package com.example.pagaopiu.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuotaResponseDTO {
    private Long id;
    private Integer numero;
    private LocalDate fechaVencimiento;
    private BigDecimal capital;
    private BigDecimal interes;
    private BigDecimal monto;
    private BigDecimal saldoCapital;
    private String estado;
    private Long pagoId;
    private BigDecimal saldoInicial;
}
