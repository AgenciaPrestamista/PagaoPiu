package com.example.pagaopiu.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
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
public class PrestamoResponseDTO {
    private Long id;
    private String clienteDni;
    private String clienteNombre;
    private String clienteEstado;
    private Long sucursalId;
    private String sucursalNombre;
    private String asesorNombre;
    private LocalDate fechaPrestamo;
    private BigDecimal capital;
    private Integer plazoMeses;
    private BigDecimal tea;
    private BigDecimal tem;
    private BigDecimal cuotaFija;
    private String estado;
    private LocalDate fechaCancelacion;
    private List<CuotaResponseDTO> cuotas;
}
