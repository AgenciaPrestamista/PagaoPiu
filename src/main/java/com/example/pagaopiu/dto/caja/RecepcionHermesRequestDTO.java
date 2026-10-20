package com.example.pagaopiu.dto.caja;

import java.math.BigDecimal;
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
public class RecepcionHermesRequestDTO {
    private String codigoRemesa;
    private BigDecimal montoApto;
    private BigDecimal montoObservado;
    private String motivo;
}
