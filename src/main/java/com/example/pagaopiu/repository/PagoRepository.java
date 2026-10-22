package com.example.pagaopiu.repository;

import com.example.pagaopiu.model.entity.Pago;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {

    @Override
    @EntityGraph(attributePaths = {"sucursal", "usuario", "prestamo", "prestamo.cliente"})
    Optional<Pago> findById(Long id);

    @EntityGraph(attributePaths = {"sucursal", "usuario", "prestamo", "prestamo.cliente"})
    List<Pago> findByPrestamoId(Long prestamoId);

    @EntityGraph(attributePaths = {"sucursal", "usuario", "prestamo", "prestamo.cliente"})
    List<Pago> findBySucursalId(Long sucursalId);

    @Query("""
        SELECT COALESCE(SUM(p.monto), 0)
        FROM Pago p
        WHERE p.sucursal.id = :sucursalId
    """)
    BigDecimal sumMontoBySucursalId(@Param("sucursalId") Long sucursalId);

    @Query("""
        SELECT COALESCE(SUM(p.monto), 0)
        FROM Pago p
        WHERE p.sucursal.id = :sucursalId AND p.fechaPago = :fecha
    """)
    BigDecimal sumMontoBySucursalIdAndFecha(@Param("sucursalId") Long sucursalId, @Param("fecha") java.time.LocalDate fecha);

    @Query(value = "SELECT nextval('seq_numero_boleta')", nativeQuery = true)
    Long getNextBoletaSequence();

    @Query(value = "SELECT nextval('seq_numero_factura')", nativeQuery = true)
    Long getNextFacturaSequence();

    @EntityGraph(attributePaths = {"sucursal", "usuario", "prestamo", "prestamo.cliente"})
    Optional<Pago> findByNumeroComprobante(String numeroComprobante);
}
