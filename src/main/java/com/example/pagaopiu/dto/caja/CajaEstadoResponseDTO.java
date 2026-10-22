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
public class CajaEstadoResponseDTO {
    private Long sucursalId;
    private String sucursalNombre;
    private LocalDate fecha;
    private BigDecimal saldoDisponible;
    private BigDecimal montoObservadoAcumulado;
    private boolean abierta;
    private String usuarioApertura;
    private boolean cerrada;
    private String usuarioCierre;
    private LocalDateTime fechaCierre;
    private BigDecimal saldoCierre;
    private BigDecimal totalCobradoCierre;
    private BigDecimal totalCobradoHoy;
    private String observacionCierre;
}
