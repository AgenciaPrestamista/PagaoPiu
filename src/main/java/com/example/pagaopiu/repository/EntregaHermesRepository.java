package com.example.pagaopiu.repository;

import com.example.pagaopiu.model.entity.EntregaHermes;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EntregaHermesRepository extends JpaRepository<EntregaHermes, Long> {

    boolean existsByCodigoRemesa(String codigoRemesa);

    Optional<EntregaHermes> findByCodigoRemesa(String codigoRemesa);

    List<EntregaHermes> findBySucursalIdAndFecha(Long sucursalId, LocalDate fecha);
}
