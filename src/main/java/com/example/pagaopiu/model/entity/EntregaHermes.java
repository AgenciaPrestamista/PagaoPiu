package com.example.pagaopiu.model.entity;

import com.example.pagaopiu.model.enums.EstadoEntregaHermes;
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
@Table(name = "entrega_hermes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntregaHermes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_id")
    private CajaSucursal caja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "codigo_remesa", nullable = false, unique = true, length = 50)
    private String codigoRemesa;

    @Column(name = "monto_esperado", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal montoEsperado = new BigDecimal("50000.00");

    @Column(name = "monto_apto", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoApto;

    @Column(name = "monto_observado", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal montoObservado = BigDecimal.ZERO;

    @Column(length = 500)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoEntregaHermes estado;

    @Column(name = "fecha_recepcion", nullable = false)
    private LocalDateTime fechaRecepcion;
}
