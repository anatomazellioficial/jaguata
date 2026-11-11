package com.jaguata.plataforma.repository;

import com.jaguata.plataforma.domain.Vaga;
import com.jaguata.plataforma.domain.enums.StatusVaga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VagaRepository extends JpaRepository<Vaga, UUID> {
    List<Vaga> findByEmpresaId(UUID empresaId);

    List<Vaga> findByStatus(StatusVaga status);

    long countByStatus(StatusVaga status);

    boolean existsByIdAndEmpresaId(UUID id, UUID empresaId);
}
