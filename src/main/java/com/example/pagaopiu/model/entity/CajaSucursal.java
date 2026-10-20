package com.example.pagaopiu.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "caja_sucursal", uniqueConstraints = {
    @UniqueConstraint(name = "uq_caja_sucursal_fecha", columnNames = {"sucursal_id", "fecha"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CajaSucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "saldo_disponible", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal saldoDisponible = BigDecimal.ZERO;

    @Column(name = "monto_observado_acumulado", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal montoObservadoAcumulado = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private boolean abierta = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_apertura_id")
    private Usuario usuarioApertura;

    @Column(name = "fecha_apertura")
    private LocalDateTime fechaApertura;
}
