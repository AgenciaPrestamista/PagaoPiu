package com.example.pagaopiu.repository;

import com.example.pagaopiu.model.entity.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @EntityGraph(attributePaths = {"sucursal"})
    Optional<Usuario> findByUsername(String username);
}
