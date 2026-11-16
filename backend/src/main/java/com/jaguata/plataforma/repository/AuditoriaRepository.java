package com.jaguata.plataforma.repository;

import com.jaguata.plataforma.domain.Auditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditoriaRepository extends JpaRepository<Auditoria, UUID> {
    Page<Auditoria> findByAcaoOrderByQuandoDesc(String acao, Pageable pageable);

    Page<Auditoria> findAllByOrderByQuandoDesc(Pageable pageable);
}
