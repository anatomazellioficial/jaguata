package com.jaguata.plataforma.repository;

import com.jaguata.plataforma.domain.Candidato;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CandidatoRepository extends JpaRepository<Candidato, UUID> {

    List<Candidato> findByOngId(UUID ongId);

    long countByOngIdAndAnonimizadoEmIsNull(UUID ongId);

    Page<Candidato> findByOngIdAndAnonimizadoEmIsNullOrderByNomeCompletoAsc(UUID ongId, Pageable pageable);

    /**
     * Candidatos elegíveis para a vaga, ordenados do mais próximo ao mais distante.
     * <ul>
     *   <li>apenas ONGs verificadas e vaga ABERTA;</li>
     *   <li>raio (PostGIS ST_DWithin, em metros) — ignorado para vagas REMOTO;</li>
     *   <li>vaga de APRENDIZ só aceita 18 a 24 anos;</li>
     *   <li>exclui candidatos que já têm match com a vaga;</li>
     *   <li>exclui candidatos que já foram CONTRATADOS (em qualquer vaga);</li>
     *   <li>exclui candidatos anonimizados (direito de eliminação);</li>
     *   <li>vaga de ESTAGIÁRIO só aceita quem cursa ensino técnico ou superior;</li>
     *   <li>filtros opcionais: modelo de trabalho preferido e escolaridade mínima (os demais filtros e o score
     *       são aplicados em Java, sobre este conjunto, que é limitado a {@code teto} candidatos).</li>
     * </ul>
     */
    @Query(nativeQuery = true, value = """
            SELECT c.id AS "candidatoId",
                   ST_Distance(c.localizacao, v.localizacao) AS "distanciaMetros"
              FROM candidatos c
              JOIN ongs o  ON o.id = c.ong_id
              JOIN vagas v ON v.id = :vagaId
             WHERE o.is_verified = TRUE
               AND c.anonimizado_em IS NULL
               AND v.status = 'ABERTA'
               AND (v.modelo_trabalho = 'REMOTO'
                    OR ST_DWithin(c.localizacao, v.localizacao, :raioMetros))
               AND (v.nivel_cargo <> 'APRENDIZ'
                    OR c.data_nascimento > (CURRENT_DATE - INTERVAL '25 years'))
               AND (v.nivel_cargo <> 'ESTAGIARIO'
                    OR c.escolaridade IN ('TECNICO_INCOMPLETO', 'SUPERIOR_INCOMPLETO'))
               AND (CAST(:modelo AS text) IS NULL OR c.modelo_trabalho_pref::text = CAST(:modelo AS text))
               AND (CAST(:nascimentoAte AS date) IS NULL OR c.data_nascimento <= CAST(:nascimentoAte AS date))
               AND (CAST(:nascimentoDesde AS date) IS NULL OR c.data_nascimento >= CAST(:nascimentoDesde AS date))
               AND (CAST(:escolaridadeMin AS text) IS NULL
                    OR c.escolaridade >= CAST(CAST(:escolaridadeMin AS text) AS escolaridade))
               AND NOT EXISTS (SELECT 1 FROM matches m
                                WHERE m.vaga_id = v.id AND m.candidato_id = c.id)
               AND NOT EXISTS (SELECT 1 FROM matches h
                                WHERE h.candidato_id = c.id AND h.status = 'CONTRATADO')
             ORDER BY 2
             LIMIT :teto
            """)
    List<CandidatoProximo> buscarElegiveisParaVaga(@Param("vagaId") UUID vagaId,
                                                   @Param("raioMetros") double raioMetros,
                                                   @Param("modelo") String modelo,
                                                   @Param("escolaridadeMin") String escolaridadeMin,
                                                   @Param("nascimentoAte") java.time.LocalDate nascimentoAte,
                                                   @Param("nascimentoDesde") java.time.LocalDate nascimentoDesde,
                                                   @Param("teto") int teto);
}
