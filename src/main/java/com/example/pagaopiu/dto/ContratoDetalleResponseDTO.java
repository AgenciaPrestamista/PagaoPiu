package com.example.pagaopiu.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class ContratoDetalleResponseDTO {
    private Long id;
    private String numeroContrato;
    private String clienteDni;
    private String clienteNombre;
    private String clienteEstado;
    private Long sucursalId;
    private String sucursalNombre;
    private String asesorNombre;
    private BigDecimal capital;
    private Integer plazoMeses;
    private BigDecimal tea;
    private BigDecimal tem;
    private BigDecimal cuotaFija;
    private LocalDate fechaPrestamo;
    private String estado;
    private Boolean impreso;
    private LocalDateTime fechaImpresion;
    private String motivoAnulacion;
    private boolean tieneContratoFirmado;
    private String contratoNombreOriginal;
    private LocalDateTime fechaSubidaContrato;
    private List<CuotaResponseDTO> cuotas;
    private String textoContrato;
    private String bloqueFirmas;
    private boolean tieneFirmaGerente;
    private BigDecimal temPorcentajeCuatroDecimales;
    private String ciudadContrato;
    private String fechaEmisionFormateada;
    private String emisorRazonSocial;
    private String empresaRazonSocial;
    private String empresaRuc;
    private String empresaRepresentanteNombre;
    private String empresaRepresentanteDocumento;
    private String empresaRepresentanteCargo;
}
