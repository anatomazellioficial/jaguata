package com.jaguata.plataforma.repository;

import com.jaguata.plataforma.domain.Ong;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OngRepository extends JpaRepository<Ong, UUID> {
    Optional<Ong> findByCnpj(String cnpj);

    boolean existsByCnpj(String cnpj);

    List<Ong> findAllByOrderByCreatedAtDesc();

    List<Ong> findByVerifiedOrderByCreatedAtDesc(boolean verified);

    long countByVerified(boolean verified);
}
