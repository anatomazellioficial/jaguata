package com.jaguata.plataforma;

import com.jaguata.plataforma.domain.*;
import com.jaguata.plataforma.domain.enums.*;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.repository.*;
import com.jaguata.plataforma.service.MatchService;
import com.jaguata.plataforma.service.VagaService;
import com.jaguata.plataforma.service.VagaService.CriarVagaCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Valida migration Flyway, constraints do banco e a busca geográfica em PostGIS real. */
@SpringBootTest
@Testcontainers
@Transactional
class IntegracaoPostgisTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @Autowired EmpresaRepository empresas;
    @Autowired OngRepository ongs;
    @Autowired CandidatoRepository candidatos;
    @Autowired VagaService vagaService;
    @Autowired MatchService matchService;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    // Sé (SP) | Av. Paulista (~3 km da Sé) | Campinas (~85 km)
    static final double[] SE = {-23.5505, -46.6333};
    static final double[] PAULISTA = {-23.5614, -46.6559};
    static final double[] CAMPINAS = {-22.9099, -47.0626};

    Empresa empresaME;
    Ong ong;

    @BeforeEach
    void setUp() {
        empresaME = new Empresa();
        empresaME.setCnpj("11222333000181");
        empresaME.setRazaoSocial("Tech ME Ltda");
        empresaME.setCnaePrincipal("6201501");
        empresaME.setPorteReceita(PorteEmpresa.ME);
        empresaME.setCep("01001000");
        empresaME.setLat(SE[0]);
        empresaME.setLng(SE[1]);
        empresaME = empresas.saveAndFlush(empresaME);

        ong = new Ong();
        ong.setCnpj("99888777000155");
        ong.setRazaoSocial("ONG Futuro");
        ong.setCnaePrincipal("6209100");
        ong.setCep("01310100");
        ong.setLat(PAULISTA[0]);
        ong.setLng(PAULISTA[1]);
        ong.setVerified(true);
        ong = ongs.saveAndFlush(ong);
    }

    Candidato candidato(String nome, int idade, double[] pos, ModeloTrabalho pref) {
        Candidato c = new Candidato();
        c.setOng(ong);
        c.setNomeCompleto(nome);
        c.setDataNascimento(LocalDate.now().minusYears(idade).minusDays(1));
        c.setCep("01310100");
        c.setLat(pos[0]);
        c.setLng(pos[1]);
        c.setModeloTrabalhoPref(pref);
        return candidatos.saveAndFlush(c);
    }

    Vaga vaga(NivelCargo nivel, ModeloTrabalho modelo) {
        return vagaService.criar(empresaME.getId(),
                new CriarVagaCommand(nivel, TipoContrato.FIXO, modelo, "01001000", SE[0], SE[1]));
    }

    @Test
    void bancoRejeitaCandidatoMenorDeIdade() {
        UUID ongId = ong.getId();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO candidatos (ong_id, nome_completo, data_nascimento, cep, lat, lng, modelo_trabalho_pref)
                VALUES (?, 'Menor', ?, '01310100', -23.5, -46.6, 'REMOTO')
                """, ongId, LocalDate.now().minusYears(17)));
    }

    @Test
    void bancoAceitaCandidatoComDezoitoAnosCompletos() {
        UUID ongId = ong.getId();
        int n = jdbc.update("""
                INSERT INTO candidatos (ong_id, nome_completo, data_nascimento, cep, lat, lng, modelo_trabalho_pref)
                VALUES (?, 'Adulto', ?, '01310100', -23.5, -46.6, 'REMOTO')
                """, ongId, LocalDate.now().minusYears(18));
        assertEquals(1, n);
    }

    @Test
    void vagaDeDiretorEmMicroEmpresaApareceComoCoordenadorGerente() {
        Vaga v = vaga(NivelCargo.DIRETOR, ModeloTrabalho.PRESENCIAL);
        assertEquals("Coordenador/Gerente", v.getTituloExibicao());
        em.flush();
        String noBanco = jdbc.queryForObject(
                "SELECT titulo_exibicao FROM vagas WHERE id = ?", String.class, v.getId());
        assertEquals("Coordenador/Gerente", noBanco);
    }

    @Test
    void matchRespeitaRaioEOrdenaPorScore() {
        Candidato perto = candidato("Perto", 30, PAULISTA, ModeloTrabalho.PRESENCIAL);
        candidato("Longe", 30, CAMPINAS, ModeloTrabalho.PRESENCIAL);
        Vaga v = vaga(NivelCargo.ANALISTA, ModeloTrabalho.PRESENCIAL);

        List<Match> sugestoes = matchService.gerarSugestoes(v.getId(), 10, 50);

        assertEquals(1, sugestoes.size());
        assertEquals(perto.getId(), sugestoes.get(0).getCandidato().getId());
        assertEquals(StatusMatch.SUGERIDO, sugestoes.get(0).getStatus());
        assertTrue(sugestoes.get(0).getMatchScore().doubleValue() > 0);
    }

    @Test
    void vagaRemotaIgnoraRaio() {
        candidato("Longe", 30, CAMPINAS, ModeloTrabalho.REMOTO);
        Vaga v = vaga(NivelCargo.ANALISTA, ModeloTrabalho.REMOTO);

        assertEquals(1, matchService.gerarSugestoes(v.getId(), 1, 50).size());
    }

    @Test
    void vagaDeAprendizSoAceita18a24Anos() {
        Candidato jovem = candidato("Jovem", 22, PAULISTA, ModeloTrabalho.PRESENCIAL);
        candidato("Veterano", 30, PAULISTA, ModeloTrabalho.PRESENCIAL);
        Vaga v = vaga(NivelCargo.APRENDIZ, ModeloTrabalho.PRESENCIAL);

        List<Match> sugestoes = matchService.gerarSugestoes(v.getId(), 10, 50);

        assertEquals(1, sugestoes.size());
        assertEquals(jovem.getId(), sugestoes.get(0).getCandidato().getId());
    }

    @Test
    void ongNaoVerificadaNaoGeraMatch() {
        ong.setVerified(false);
        ongs.saveAndFlush(ong);
        candidato("Perto", 30, PAULISTA, ModeloTrabalho.PRESENCIAL);
        Vaga v = vaga(NivelCargo.ANALISTA, ModeloTrabalho.PRESENCIAL);

        assertTrue(matchService.gerarSugestoes(v.getId(), 10, 50).isEmpty());
    }

    @Test
    void naoGeraMatchDuplicadoEFunilBloqueiaPulos() {
        candidato("Perto", 30, PAULISTA, ModeloTrabalho.PRESENCIAL);
        Vaga v = vaga(NivelCargo.ANALISTA, ModeloTrabalho.PRESENCIAL);

        Match m = matchService.gerarSugestoes(v.getId(), 10, 50).get(0);
        assertTrue(matchService.gerarSugestoes(v.getId(), 10, 50).isEmpty());

        assertThrows(RegraNegocioException.class,
                () -> matchService.avancar(m.getId(), StatusMatch.CONTRATADO));
        assertEquals(StatusMatch.ENVIADO, matchService.avancar(m.getId(), StatusMatch.ENVIADO).getStatus());
    }

    // ---------- contratação fecha a vaga ----------
    @Autowired MatchRepository matchRepo;
    @Autowired VagaRepository vagaRepo;

    private StatusMatch statusDe(Match m) {
        return matchRepo.findById(m.getId()).orElseThrow().getStatus();
    }

    private Match deCandidato(List<Match> ms, Candidato c) {
        return ms.stream().filter(m -> m.getCandidato().getId().equals(c.getId())).findFirst().orElseThrow();
    }

    @Test
    void contratarPreencheAVagaEEncerraAsDemaisPendencias() {
        Candidato ana = candidato("Ana", 30, PAULISTA, ModeloTrabalho.PRESENCIAL);
        Candidato bia = candidato("Bia", 28, PAULISTA, ModeloTrabalho.PRESENCIAL);
        Vaga v = vaga(NivelCargo.ANALISTA, ModeloTrabalho.PRESENCIAL);
        List<Match> sugestoes = matchService.gerarSugestoes(v.getId(), 10, 50);
        Match ma = deCandidato(sugestoes, ana);
        Match mb = deCandidato(sugestoes, bia);

        matchService.avancar(ma.getId(), StatusMatch.ENVIADO);
        matchService.avancar(ma.getId(), StatusMatch.ENTREVISTA);
        matchService.avancar(mb.getId(), StatusMatch.ENVIADO);
        matchService.avancar(ma.getId(), StatusMatch.CONTRATADO);
        em.flush();
        em.clear();

        assertEquals(StatusVaga.PREENCHIDA, vagaRepo.findById(v.getId()).orElseThrow().getStatus());
        assertEquals(StatusMatch.CONTRATADO, statusDe(ma));
        assertEquals(StatusMatch.RECUSADO, statusDe(mb), "a pendência concorrente da vaga é encerrada");
    }

    @Test
    void candidatoContratadoNaoEElegivelParaOutraVaga() {
        candidato("Ana", 30, PAULISTA, ModeloTrabalho.PRESENCIAL);
        Vaga v1 = vaga(NivelCargo.ANALISTA, ModeloTrabalho.PRESENCIAL);
        Vaga v2 = vaga(NivelCargo.ASSISTENTE, ModeloTrabalho.PRESENCIAL);
        Match m = matchService.gerarSugestoes(v1.getId(), 10, 50).get(0);
        matchService.avancar(m.getId(), StatusMatch.ENVIADO);
        matchService.avancar(m.getId(), StatusMatch.ENTREVISTA);
        matchService.avancar(m.getId(), StatusMatch.CONTRATADO);
        em.flush();
        em.clear();

        assertTrue(matchService.gerarSugestoes(v2.getId(), 10, 50).isEmpty());
    }

    @Test
    void naoSeContrataDuasVezesOMesmoCandidato() {
        candidato("Ana", 30, PAULISTA, ModeloTrabalho.PRESENCIAL);
        Vaga v1 = vaga(NivelCargo.ANALISTA, ModeloTrabalho.PRESENCIAL);
        Vaga v2 = vaga(NivelCargo.ASSISTENTE, ModeloTrabalho.PRESENCIAL);
        // as duas sugestões nascem antes de qualquer contratação (corrida entre duas empresas)
        Match m1 = matchService.gerarSugestoes(v1.getId(), 10, 50).get(0);
        Match m2 = matchService.gerarSugestoes(v2.getId(), 10, 50).get(0);
        for (Match m : List.of(m1, m2)) {
            matchService.avancar(m.getId(), StatusMatch.ENVIADO);
            matchService.avancar(m.getId(), StatusMatch.ENTREVISTA);
        }
        matchService.avancar(m1.getId(), StatusMatch.CONTRATADO);
        em.flush();
        em.clear();

        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> matchService.avancar(m2.getId(), StatusMatch.CONTRATADO));
        assertTrue(e.getMessage().contains("já foi contratado"));
    }

    @Test
    void naoSeAvancaEmVagaForaDoAr() {
        candidato("Ana", 30, PAULISTA, ModeloTrabalho.PRESENCIAL);
        Vaga v = vaga(NivelCargo.ANALISTA, ModeloTrabalho.PRESENCIAL);
        Match m = matchService.gerarSugestoes(v.getId(), 10, 50).get(0);

        vagaService.alterarStatus(v.getId(), StatusVaga.PAUSADA);
        assertThrows(RegraNegocioException.class, () -> matchService.avancar(m.getId(), StatusMatch.ENVIADO));
        // recusar continua permitido
        assertEquals(StatusMatch.RECUSADO, matchService.avancar(m.getId(), StatusMatch.RECUSADO).getStatus());
    }
}
