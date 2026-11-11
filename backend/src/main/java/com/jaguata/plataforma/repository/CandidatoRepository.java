package com.jaguata.plataforma.repository;

import com.jaguata.plataforma.domain.Candidato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CandidatoRepository extends JpaRepository<Candidato, UUID> {

    List<Candidato> findByOngId(UUID ongId);

    /**
     * Candidatos elegíveis para a vaga, ordenados do mais próximo ao mais distante.
     * <ul>
     *   <li>apenas ONGs verificadas e vaga ABERTA;</li>
     *   <li>raio (PostGIS ST_DWithin, em metros) — ignorado para vagas REMOTO;</li>
     *   <li>vaga de APRENDIZ só aceita 18 a 24 anos;</li>
     *   <li>exclui candidatos que já têm match com a vaga.</li>
     * </ul>
     */
    @Query(nativeQuery = true, value = """
            SELECT c.id AS "candidatoId",
                   ST_Distance(c.localizacao, v.localizacao) AS "distanciaMetros"
              FROM candidatos c
              JOIN ongs o  ON o.id = c.ong_id
              JOIN vagas v ON v.id = :vagaId
             WHERE o.is_verified = TRUE
               AND v.status = 'ABERTA'
               AND (v.modelo_trabalho = 'REMOTO'
                    OR ST_DWithin(c.localizacao, v.localizacao, :raioMetros))
               AND (v.nivel_cargo <> 'APRENDIZ'
                    OR c.data_nascimento > (CURRENT_DATE - INTERVAL '25 years'))
               AND NOT EXISTS (SELECT 1 FROM matches m
                                WHERE m.vaga_id = v.id AND m.candidato_id = c.id)
             ORDER BY 2
             LIMIT :limite
            """)
    List<CandidatoProximo> buscarElegiveisParaVaga(@Param("vagaId") UUID vagaId,
                                                   @Param("raioMetros") double raioMetros,
                                                   @Param("limite") int limite);
}
