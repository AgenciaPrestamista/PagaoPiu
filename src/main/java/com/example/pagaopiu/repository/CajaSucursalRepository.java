package com.example.pagaopiu.repository;

import com.example.pagaopiu.model.entity.CajaSucursal;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CajaSucursalRepository extends JpaRepository<CajaSucursal, Long> {

    Optional<CajaSucursal> findBySucursalIdAndFecha(Long sucursalId, LocalDate fecha);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CajaSucursal c WHERE c.sucursal.id = :sucursalId AND c.fecha = :fecha")
    Optional<CajaSucursal> findBySucursalIdAndFechaWithLock(@Param("sucursalId") Long sucursalId, @Param("fecha") LocalDate fecha);
}
