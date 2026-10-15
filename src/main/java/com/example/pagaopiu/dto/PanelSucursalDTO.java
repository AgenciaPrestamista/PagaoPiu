package com.example.pagaopiu.dto;

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
public class PanelSucursalDTO {
    private Long sucursalId;
    private String sucursalNombre;
    private String sucursalCodigo;
    private long prestamosActivos;
    private long prestamosVigentes;
    private long prestamosMorosos;
    private long prestamosExtrajudiciales;
    private BigDecimal montoTotalCobrado;
    private BigDecimal interesesCobrados;
}
