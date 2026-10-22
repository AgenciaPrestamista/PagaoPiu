package com.example.pagaopiu.repository;

import com.example.pagaopiu.model.entity.CajaSucursal;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CajaSucursalRepository extends JpaRepository<CajaSucursal, Long> {

    Optional<CajaSucursal> findBySucursalIdAndFecha(Long sucursalId, LocalDate fecha);

    @Query("""
        SELECT c FROM CajaSucursal c
        JOIN FETCH c.sucursal
        LEFT JOIN FETCH c.usuarioApertura
        LEFT JOIN FETCH c.usuarioCierre
        WHERE c.fecha BETWEEN :desde AND :hasta
        ORDER BY c.fecha DESC, c.sucursal.nombre ASC
    """)
    List<CajaSucursal> findByRangoFechas(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CajaSucursal c WHERE c.sucursal.id = :sucursalId AND c.fecha = :fecha")
    Optional<CajaSucursal> findBySucursalIdAndFechaWithLock(@Param("sucursalId") Long sucursalId, @Param("fecha") LocalDate fecha);
}
