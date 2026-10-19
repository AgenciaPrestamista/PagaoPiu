package com.example.pagaopiu.dto;

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
public class ContratoResponseDTO {
    private Long id;
    private String numeroContrato;
    private String clienteDni;
    private String clienteNombre;
    private Long sucursalId;
    private String sucursalNombre;
    private String asesorNombre;
    private BigDecimal capital;
    private Integer plazoMeses;
    private BigDecimal tea;
    private BigDecimal cuotaFija;
    private LocalDate fechaPrestamo;
    private String estado;
    private Boolean impreso;
    private LocalDateTime fechaImpresion;
    private String motivoAnulacion;
    private boolean tieneContratoFirmado;
    private String contratoNombreOriginal;
    private LocalDateTime fechaSubidaContrato;
}
