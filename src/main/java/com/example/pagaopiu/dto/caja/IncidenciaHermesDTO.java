package com.example.pagaopiu.dto.caja;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class IncidenciaHermesDTO {
    private Long id;
    private Long entregaId;
    private Long sucursalId;
    private String sucursalNombre;
    private String usuarioReporta;
    private LocalDate fecha;
    private BigDecimal montoEsperado;
    private BigDecimal montoApto;
    private BigDecimal montoObservado;
    private BigDecimal diferencia;
    private String motivo;
    private String estado;
    private String resolucion;
    private String usuarioCierre;
    private LocalDateTime fechaCierre;
}
