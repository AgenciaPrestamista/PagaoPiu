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
public class PagoResponseDTO {
    private Long id;
    private Long prestamoId;
    private String sucursalNombre;
    private String asesorNombre;
    private LocalDate fechaPago;
    private BigDecimal monto;
    private String tipo;
    private String numeroComprobante;
    private String tipoComprobante;
    private String clienteNombre;
    private String clienteDocumento;
    private String clienteDireccion;
    private Integer cuotaNumero;
    private BigDecimal montoCapital;
    private BigDecimal montoInteres;
    private BigDecimal saldoRestante;
}
