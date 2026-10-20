package com.example.pagaopiu.dto.caja;

import com.example.pagaopiu.model.enums.EstadoEntregaHermes;
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
public class RecepcionHermesResponseDTO {
    private Long entregaId;
    private EstadoEntregaHermes estado;
    private BigDecimal montoEsperado;
    private BigDecimal montoApto;
    private BigDecimal montoObservado;
    private BigDecimal saldoCajaDisponible;
    private boolean incidenciaGenerada;
    private Long incidenciaId;
    private String mensaje;
}
