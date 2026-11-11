package com.jaguata.plataforma.repository;

import com.jaguata.plataforma.domain.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {
    Optional<Empresa> findByCnpj(String cnpj);

    boolean existsByCnpj(String cnpj);
}
