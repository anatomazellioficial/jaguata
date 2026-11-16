package com.jaguata.plataforma.repository;

import com.jaguata.plataforma.domain.NotificacaoPendente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificacaoPendenteRepository extends JpaRepository<NotificacaoPendente, UUID> {
    List<NotificacaoPendente> findAllByOrderByUsuarioIdAscCriadaEmAsc();
}
