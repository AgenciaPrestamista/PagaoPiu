package com.example.pagaopiu.repository;

import com.example.pagaopiu.model.entity.ReniecPadron;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReniecPadronRepository extends JpaRepository<ReniecPadron, String> {
    Optional<ReniecPadron> findByDni(String dni);
}
