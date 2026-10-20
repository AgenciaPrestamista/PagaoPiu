package com.example.pagaopiu.repository;

import com.example.pagaopiu.model.entity.IncidenciaHermes;
import com.example.pagaopiu.model.enums.EstadoIncidenciaHermes;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidenciaHermesRepository extends JpaRepository<IncidenciaHermes, Long> {

    List<IncidenciaHermes> findByEstadoOrderByFechaDescIdDesc(EstadoIncidenciaHermes estado);

    List<IncidenciaHermes> findByEstadoAndSucursalIdOrderByFechaDescIdDesc(EstadoIncidenciaHermes estado, Long sucursalId);

    List<IncidenciaHermes> findAllByOrderByIdDesc();

    @Query("SELECT i FROM IncidenciaHermes i WHERE (:estado IS NULL OR i.estado = :estado) AND (:sucursalId IS NULL OR i.sucursal.id = :sucursalId) ORDER BY i.id DESC")
    List<IncidenciaHermes> filtrarIncidencias(
            @Param("estado") EstadoIncidenciaHermes estado,
            @Param("sucursalId") Long sucursalId);
}
