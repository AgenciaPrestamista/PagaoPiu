package com.example.pagaopiu.dto;

import java.math.BigDecimal;
import java.util.List;
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
public class PanelResponseDTO {
    private long clientesMorosos;
    private long clientesExtrajudiciales;
    private List<PanelSucursalDTO> sucursales;
    private long totalPrestamosActivos;
    private long totalPrestamosVigentes;
    private long totalPrestamosMorosos;
    private long totalPrestamosExtrajudiciales;
    private BigDecimal totalMontoCobrado;
    private BigDecimal totalInteresesCobrados;
    private BigDecimal totalMontoColocado;
    private long totalClientes;
}
