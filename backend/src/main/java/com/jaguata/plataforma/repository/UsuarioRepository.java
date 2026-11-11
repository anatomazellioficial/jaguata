package com.jaguata.plataforma.repository;

import com.jaguata.plataforma.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
