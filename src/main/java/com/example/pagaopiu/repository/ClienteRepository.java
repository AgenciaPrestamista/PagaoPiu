package com.example.pagaopiu.repository;

import com.example.pagaopiu.model.entity.Cliente;
import com.example.pagaopiu.model.enums.EstadoCliente;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByDni(String dni);

    long countByEstado(EstadoCliente estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cliente c WHERE c.id = :id")
    Optional<Cliente> findByIdWithLock(@Param("id") Long id);

    /**
     * Clientes que representan al mismo titular: el DNI (8 dígitos) y cualquier RUC 10 derivado de él
     * (RUC = "10" + DNI + dígito verificador). Recibe el DNI de 8 dígitos.
     */
    @Query("SELECT c FROM Cliente c WHERE c.dni = :dni OR (LENGTH(c.dni) = 11 AND SUBSTRING(c.dni, 1, 2) = '10' AND SUBSTRING(c.dni, 3, 8) = :dni)")
    List<Cliente> findRelacionadosPorDni(@Param("dni") String dni);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cliente c WHERE c.dni = :dni")
    Optional<Cliente> findByDniWithLock(@Param("dni") String dni);
}
