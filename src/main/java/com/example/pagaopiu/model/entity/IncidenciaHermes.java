package com.example.pagaopiu.model.entity;

import com.example.pagaopiu.model.enums.EstadoIncidenciaHermes;
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
@Table(name = "incidencia_hermes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidenciaHermes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entrega_id")
    private EntregaHermes entrega;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_reporta_id", nullable = false)
    private Usuario usuarioReporta;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "monto_esperado", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoEsperado;

    @Column(name = "monto_apto", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoApto;

    @Column(name = "monto_observado", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoObservado;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal diferencia;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoIncidenciaHermes estado = EstadoIncidenciaHermes.ABIERTA;

    @Column(length = 500)
    private String resolucion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_cierre_id")
    private Usuario usuarioCierre;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;
}
