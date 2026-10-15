package com.example.pagaopiu.repository;

import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

    @Override
    @EntityGraph(attributePaths = {"cliente", "sucursal", "asesor"})
    Optional<Prestamo> findById(Long id);

    @EntityGraph(attributePaths = {"cliente", "sucursal", "asesor"})
    List<Prestamo> findByClienteId(Long clienteId);

    @EntityGraph(attributePaths = {"cliente", "sucursal", "asesor"})
    List<Prestamo> findBySucursalId(Long sucursalId);

    @EntityGraph(attributePaths = {"cliente", "sucursal", "asesor"})
    List<Prestamo> findByClienteIdAndEstadoNot(Long clienteId, EstadoPrestamo estado);

    @EntityGraph(attributePaths = {"cliente", "sucursal", "asesor"})
    @Query("SELECT p FROM Prestamo p WHERE p.cliente.dni = :dni ORDER BY p.id DESC")
    List<Prestamo> findByClienteDni(@Param("dni") String dni);

    @EntityGraph(attributePaths = {"cliente", "sucursal", "asesor"})
    @Query("SELECT p FROM Prestamo p ORDER BY p.id DESC")
    List<Prestamo> findAllOrderByFechaDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Prestamo p WHERE p.id = :id")
    Optional<Prestamo> findByIdWithLock(@Param("id") Long id);

    @Query("""
        SELECT COALESCE(SUM(c.capital), 0)
        FROM Cuota c
        JOIN c.prestamo p
        WHERE p.cliente.id = :clienteId
          AND p.estado <> com.example.pagaopiu.model.enums.EstadoPrestamo.CANCELADO
          AND c.estado = com.example.pagaopiu.model.enums.EstadoCuota.PENDIENTE
    """)
    BigDecimal findSaldoCapitalPendienteByClienteId(@Param("clienteId") Long clienteId);

    @Query("""
        SELECT COUNT(p)
        FROM Prestamo p
        WHERE p.sucursal.id = :sucursalId
          AND p.estado <> com.example.pagaopiu.model.enums.EstadoPrestamo.CANCELADO
    """)
    long countActivosBySucursalId(@Param("sucursalId") Long sucursalId);

    @Query("""
        SELECT COUNT(p)
        FROM Prestamo p
        WHERE p.sucursal.id = :sucursalId
          AND p.estado = :estado
    """)
    long countBySucursalIdAndEstado(@Param("sucursalId") Long sucursalId, @Param("estado") EstadoPrestamo estado);
}
