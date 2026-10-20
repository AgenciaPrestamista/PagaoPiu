package com.example.pagaopiu.repository;

import com.example.pagaopiu.model.entity.Prestamo;
import com.example.pagaopiu.model.enums.EstadoPrestamo;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
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
          AND p.estado NOT IN (com.example.pagaopiu.model.enums.EstadoPrestamo.CANCELADO, com.example.pagaopiu.model.enums.EstadoPrestamo.ANULADO)
          AND c.estado = com.example.pagaopiu.model.enums.EstadoCuota.PENDIENTE
    """)
    BigDecimal findSaldoCapitalPendienteByClienteId(@Param("clienteId") Long clienteId);

    @Query("""
        SELECT COUNT(p)
        FROM Prestamo p
        WHERE p.sucursal.id = :sucursalId
          AND p.estado IN (com.example.pagaopiu.model.enums.EstadoPrestamo.VIGENTE, com.example.pagaopiu.model.enums.EstadoPrestamo.MOROSO, com.example.pagaopiu.model.enums.EstadoPrestamo.EXTRAJUDICIAL)
    """)
    long countActivosBySucursalId(@Param("sucursalId") Long sucursalId);

    @Query("""
        SELECT COUNT(p)
        FROM Prestamo p
        WHERE p.sucursal.id = :sucursalId
          AND p.estado = :estado
    """)
    long countBySucursalIdAndEstado(@Param("sucursalId") Long sucursalId, @Param("estado") EstadoPrestamo estado);

    @Query("""
        SELECT COALESCE(SUM(p.capital), 0)
        FROM Prestamo p
        WHERE p.sucursal.id = :sucursalId
          AND p.estado != com.example.pagaopiu.model.enums.EstadoPrestamo.ANULADO
    """)
    BigDecimal sumCapitalColocadoBySucursalId(@Param("sucursalId") Long sucursalId);

    @Query("""
        SELECT COALESCE(SUM(p.capital), 0)
        FROM Prestamo p
        WHERE p.estado != com.example.pagaopiu.model.enums.EstadoPrestamo.ANULADO
    """)
    BigDecimal sumTotalCapitalColocado();

    @Query("""
        SELECT COUNT(DISTINCT p.cliente.id)
        FROM Prestamo p
        WHERE p.sucursal.id = :sucursalId
          AND p.estado != com.example.pagaopiu.model.enums.EstadoPrestamo.ANULADO
    """)
    long countClientesBySucursalId(@Param("sucursalId") Long sucursalId);

    @Query("""
        SELECT COUNT(DISTINCT p.cliente.id)
        FROM Prestamo p
        WHERE p.estado != com.example.pagaopiu.model.enums.EstadoPrestamo.ANULADO
    """)
    long countTotalClientes();

    @EntityGraph(attributePaths = {"cliente", "sucursal", "asesor"})
    Optional<Prestamo> findByNumeroContrato(String numeroContrato);

    boolean existsByContratoHash(String contratoHash);

    boolean existsByClienteIdAndEstado(Long clienteId, EstadoPrestamo estado);

    @EntityGraph(attributePaths = {"cliente", "sucursal", "asesor"})
    List<Prestamo> findBySucursalIdAndEstadoOrderByIdDesc(Long sucursalId, EstadoPrestamo estado);

    @EntityGraph(attributePaths = {"cliente", "sucursal", "asesor"})
    List<Prestamo> findByEstadoOrderByIdDesc(EstadoPrestamo estado);

    @EntityGraph(attributePaths = {"cliente", "sucursal", "asesor"})
    List<Prestamo> findByEstadoAndFechaPrestamoBefore(EstadoPrestamo estado, LocalDate fecha);

    @Query(value = "SELECT nextval('seq_numero_contrato')", nativeQuery = true)
    Long getNextNumeroContratoSeq();
}
