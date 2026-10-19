package com.example.pagaopiu.model.entity;

import com.example.pagaopiu.model.enums.EstadoPrestamo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prestamo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asesor_id", nullable = false)
    private Usuario asesor;

    @Column(name = "fecha_prestamo", nullable = false)
    private LocalDate fechaPrestamo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal capital;

    @Column(name = "plazo_meses", nullable = false)
    private Integer plazoMeses;

    @Column(nullable = false, precision = 15, scale = 10)
    private BigDecimal tea;

    @Column(nullable = false, precision = 15, scale = 10)
    private BigDecimal tem;

    @Column(name = "cuota_fija", nullable = false, precision = 12, scale = 2)
    private BigDecimal cuotaFija;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoPrestamo estado = EstadoPrestamo.VIGENTE;

    @Column(name = "fecha_cancelacion")
    private LocalDate fechaCancelacion;

    @Column(name = "numero_contrato", unique = true, length = 30)
    private String numeroContrato;

    @Column(nullable = false)
    @Builder.Default
    private Boolean impreso = false;

    @Column(name = "fecha_impresion")
    private LocalDateTime fechaImpresion;

    @Column(name = "motivo_anulacion", length = 500)
    private String motivoAnulacion;

    @Column(name = "contrato_nombre_original", length = 255)
    private String contratoNombreOriginal;

    @Column(name = "contrato_ruta", length = 500)
    private String contratoRuta;

    @Column(name = "contrato_hash", length = 64)
    private String contratoHash;

    @Column(name = "contrato_content_type", length = 100)
    private String contratoContentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_subido_por")
    private Usuario contratoSubidoPor;

    @Column(name = "contrato_fecha_subida")
    private LocalDateTime contratoFechaSubida;

    @Column(name = "empresa_razon_social", length = 300)
    private String empresaRazonSocial;

    @Column(name = "empresa_ruc", length = 11)
    private String empresaRuc;

    @Column(name = "empresa_representante_nombre", length = 300)
    private String empresaRepresentanteNombre;

    @Column(name = "empresa_representante_documento", length = 20)
    private String empresaRepresentanteDocumento;

    @Column(name = "empresa_representante_cargo", length = 100)
    private String empresaRepresentanteCargo;
}
