package com.example.pagaopiu.repository;

import com.example.pagaopiu.model.entity.Cuota;
import com.example.pagaopiu.model.enums.EstadoCuota;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CuotaRepository extends JpaRepository<Cuota, Long> {

    @EntityGraph(attributePaths = {"pago"})
    List<Cuota> findByPrestamoIdOrderByNumeroAsc(Long prestamoId);

    @EntityGraph(attributePaths = {"pago"})
    List<Cuota> findByPrestamoIdAndEstadoOrderByNumeroAsc(Long prestamoId, EstadoCuota estado);

    Optional<Cuota> findFirstByPrestamoIdAndEstadoOrderByNumeroAsc(Long prestamoId, EstadoCuota estado);

    @Query("""
        SELECT COALESCE(SUM(c.interes), 0)
        FROM Cuota c
        WHERE c.pago.sucursal.id = :sucursalId
          AND c.estado = com.example.pagaopiu.model.enums.EstadoCuota.PAGADA
    """)
    BigDecimal sumInteresCobradoBySucursalId(@Param("sucursalId") Long sucursalId);
}
