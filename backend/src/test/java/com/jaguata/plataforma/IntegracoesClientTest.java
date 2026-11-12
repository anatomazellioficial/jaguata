package com.jaguata.plataforma;

import com.jaguata.plataforma.domain.enums.PorteEmpresa;
import com.jaguata.plataforma.exception.ConsultaNaoEncontradaException;
import com.jaguata.plataforma.exception.EntradaInvalidaException;
import com.jaguata.plataforma.exception.ServicoExternoException;
import com.jaguata.plataforma.integracao.BrasilApiReceitaClient;
import com.jaguata.plataforma.integracao.CepClient;
import com.jaguata.plataforma.integracao.CepGeocodingClient;
import com.jaguata.plataforma.integracao.ConsultaService;
import com.jaguata.plataforma.integracao.DadosCnpj;
import com.jaguata.plataforma.integracao.Documentos;
import com.jaguata.plataforma.integracao.IntegracoesProperties;
import com.jaguata.plataforma.integracao.Localizacao;
import com.jaguata.plataforma.integracao.Localizacao.Precisao;
import com.jaguata.plataforma.integracao.ReceitaClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa os clientes REAIS (BrasilAPI e Nominatim) contra um servidor HTTP local que imita as respostas,
 * inclusive falhas e lentidão. Nenhum teste aqui usa a internet.
 */
class IntegracoesClientTest {

    record Resp(int status, String corpo, long atrasoMs) {
        Resp(int status, String corpo) {
            this(status, corpo, 0);
        }
    }

    MiniHttpServer server;
    final Map<String, Resp> respostas = new ConcurrentHashMap<>();   // chave: "cnpj" | "cep" | "nom:postalcode" ...
    final AtomicReference<String> userAgentNominatim = new AtomicReference<>();
    final AtomicInteger chamadasReceita = new AtomicInteger();

    ReceitaClient receita;
    CepClient cep;

    @BeforeEach
    void subir() throws IOException {
        server = new MiniHttpServer(req -> {
            Resp r;
            if (req.caminho().startsWith("/api/cnpj/v1/")) {
                chamadasReceita.incrementAndGet();
                r = respostas.getOrDefault("cnpj", new Resp(404, "{}"));
            } else if (req.caminho().startsWith("/api/cep/v2/")) {
                r = respostas.getOrDefault("cep", new Resp(404, "{}"));
            } else if (req.caminho().equals("/search")) {
                userAgentNominatim.set(req.cabecalhos().get("user-agent"));
                String q = req.query();
                String chave = q.contains("postalcode=") ? "nom:postalcode" : q.contains("street=") ? "nom:street" : "nom:city";
                r = respostas.getOrDefault(chave, new Resp(200, "[]"));
            } else {
                r = new Resp(404, "{}");
            }
            return new MiniHttpServer.Resposta(r.status(), r.corpo(), r.atrasoMs());
        });

        String url = server.url();
        IntegracoesProperties props = new IntegracoesProperties(url, url, "jaguata-teste/1.0", 500, 400, 0, 24, 30);
        receita = new BrasilApiReceitaClient(props);
        cep = new CepGeocodingClient(props);
    }

    @AfterEach
    void parar() {
        server.close();
    }

    static String cnpjJson(int codigoPorte, boolean mei, int situacao, String descricao, int natureza, long cnae) {
        return """
                {"razao_social":"EMPRESA X LTDA","nome_fantasia":"X","cnae_fiscal":%d,"codigo_porte":%d,
                 "opcao_pelo_mei":%b,"situacao_cadastral":%d,"descricao_situacao_cadastral":"%s",
                 "cep":"1310100","logradouro":"AV PAULISTA","bairro":"BELA VISTA","municipio":"SAO PAULO","uf":"SP",
                 "codigo_natureza_juridica":%d,"natureza_juridica":"Teste",
                 "qsa":[{"nome_socio":"FULANO DE TAL","cnpj_cpf_do_socio":"***987654**"}]}
                """.formatted(cnae, codigoPorte, mei, situacao, descricao, natureza);
    }

    static final String CNPJ = Documentos.completarCnpj("112345670001");

    // ---------------------------------------------------------------- documentos
    @Test
    void documentos_validamDigitosVerificadores() {
        assertEquals("00000000000191", Documentos.cnpj("00.000.000/0001-91"));
        assertThrows(EntradaInvalidaException.class, () -> Documentos.cnpj("00000000000192"));
        assertThrows(EntradaInvalidaException.class, () -> Documentos.cnpj("11111111111111"));
        assertThrows(EntradaInvalidaException.class, () -> Documentos.cnpj("123"));
        assertEquals("01001000", Documentos.cep("01001-000"));
        assertThrows(EntradaInvalidaException.class, () -> Documentos.cep("0100"));
    }

    // ---------------------------------------------------------------- Receita
    @Test
    void receita_mapeiaPorteMeiSituacaoENatureza() {
        respostas.put("cnpj", new Resp(200, cnpjJson(1, false, 2, "ATIVA", 2062, 6201501)));
        DadosCnpj me = receita.consultar(CNPJ);
        assertEquals(PorteEmpresa.ME, me.porte());
        assertTrue(me.ativa());
        assertEquals("6201501", me.cnaePrincipal());
        assertEquals("01310100", me.cep());              // zeros à esquerda restaurados
        assertFalse(me.entidadeSemFinsLucrativos());

        respostas.put("cnpj", new Resp(200, cnpjJson(3, false, 2, "ATIVA", 2062, 6201501)));
        assertEquals(PorteEmpresa.EPP, receita.consultar(CNPJ).porte());

        respostas.put("cnpj", new Resp(200, cnpjJson(5, false, 2, "ATIVA", 2062, 6201501)));
        assertEquals(PorteEmpresa.DEMAIS, receita.consultar(CNPJ).porte());

        respostas.put("cnpj", new Resp(200, cnpjJson(0, false, 2, "ATIVA", 2062, 6201501)));
        assertEquals(PorteEmpresa.DEMAIS, receita.consultar(CNPJ).porte());   // não informado → régua estrita

        respostas.put("cnpj", new Resp(200, cnpjJson(5, true, 2, "ATIVA", 2135, 6201501)));
        assertEquals(PorteEmpresa.MEI, receita.consultar(CNPJ).porte());      // optante pelo MEI prevalece
    }

    @Test
    void receita_identificaSituacaoInativaEEntidadeSemFinsLucrativos() {
        respostas.put("cnpj", new Resp(200, cnpjJson(1, false, 8, "BAIXADA", 3999, 9430800)));
        DadosCnpj d = receita.consultar(CNPJ);
        assertFalse(d.ativa());
        assertEquals("BAIXADA", d.situacao());
        assertTrue(d.entidadeSemFinsLucrativos());
        assertEquals("9430800", d.cnaePrincipal());
    }

    @Test
    void receita_completaCnaeComZerosEDescartaQuadroSocietario() {
        respostas.put("cnpj", new Resp(200, cnpjJson(1, false, 2, "ATIVA", 2062, 111)));
        DadosCnpj d = receita.consultar(CNPJ);
        assertEquals("0000111", d.cnaePrincipal());
        assertFalse(d.toString().contains("FULANO"), "dados de sócios não podem ser retidos");
        assertFalse(d.toString().contains("987654"));
    }

    @Test
    void receita_cnpjInexistenteViraNaoEncontrado() {
        respostas.put("cnpj", new Resp(404, "{\"message\":\"CNPJ não encontrado\"}"));
        assertThrows(ConsultaNaoEncontradaException.class, () -> receita.consultar(CNPJ));
    }

    @Test
    void receita_falhasViramServicoExterno() {
        respostas.put("cnpj", new Resp(500, "{}"));
        assertThrows(ServicoExternoException.class, () -> receita.consultar(CNPJ));

        respostas.put("cnpj", new Resp(429, "{}"));                                  // limite da fonte
        assertThrows(ServicoExternoException.class, () -> receita.consultar(CNPJ));

        respostas.put("cnpj", new Resp(200, "{\"razao_social\":\"X\"}"));            // sem CNAE
        assertThrows(ServicoExternoException.class, () -> receita.consultar(CNPJ));

        respostas.put("cnpj", new Resp(200, "isto não é json"));
        assertThrows(ServicoExternoException.class, () -> receita.consultar(CNPJ));
    }

    @Test
    void receita_respostaLentaEstouraOTimeout() {
        respostas.put("cnpj", new Resp(200, cnpjJson(1, false, 2, "ATIVA", 2062, 6201501), 1500));
        long ini = System.currentTimeMillis();
        assertThrows(ServicoExternoException.class, () -> receita.consultar(CNPJ));
        assertTrue(System.currentTimeMillis() - ini < 1400, "deve desistir no timeout de leitura, não esperar a resposta");
    }

    @Test
    void receita_servidorForaDoArViraServicoExterno() {
        server.close();
        assertThrows(ServicoExternoException.class, () -> receita.consultar(CNPJ));
    }

    // ---------------------------------------------------------------- CEP e coordenadas
    static String cepJson(String coordenadas) {
        return """
                {"cep":"01001000","state":"SP","city":"São Paulo","neighborhood":"Sé","street":"Praça da Sé",
                 "location":{"type":"Point","coordinates":%s}}
                """.formatted(coordenadas);
    }

    static final String COORD_SE = "{\"longitude\":\"-46.633081\",\"latitude\":\"-23.5503898\"}";

    @Test
    void cep_usaCoordenadasDaFontePrincipal() {
        respostas.put("cep", new Resp(200, cepJson(COORD_SE)));
        Localizacao l = cep.localizar("01001000");
        assertEquals(-23.5503898, l.lat(), 1e-7);
        assertEquals(-46.633081, l.lng(), 1e-7);
        assertEquals(Precisao.CEP, l.precisao());
        assertEquals("Praça da Sé", l.logradouro());
        assertEquals("São Paulo", l.cidade());
        assertNull(userAgentNominatim.get(), "com coordenadas na fonte principal o Nominatim nem é chamado");
    }

    @Test
    void cep_semCoordenadasCaiParaNominatimPorCep() {
        respostas.put("cep", new Resp(200, cepJson("{}")));
        respostas.put("nom:postalcode", new Resp(200, "[{\"lat\":\"-23.5505249\",\"lon\":\"-46.6336522\"}]"));
        Localizacao l = cep.localizar("01001000");
        assertEquals(-23.5505249, l.lat(), 1e-7);
        assertEquals(Precisao.CEP, l.precisao());
        assertEquals("jaguata-teste/1.0", userAgentNominatim.get(), "o Nominatim exige User-Agent identificando a aplicação");
    }

    @Test
    void cep_continuaParaRuaECidadeQuandoOCepNaoGeocodifica() {
        respostas.put("cep", new Resp(200, cepJson("{}")));
        respostas.put("nom:postalcode", new Resp(200, "[]"));
        respostas.put("nom:street", new Resp(200, "[{\"lat\":\"-23.55\",\"lon\":\"-46.63\"}]"));
        assertEquals(Precisao.RUA, cep.localizar("01001000").precisao());

        respostas.put("nom:street", new Resp(200, "[]"));
        respostas.put("nom:city", new Resp(200, "[{\"lat\":\"-23.5\",\"lon\":\"-46.6\"}]"));
        assertEquals(Precisao.CIDADE, cep.localizar("01001000").precisao());
    }

    @Test
    void cep_ignoraPontosForaDoBrasil() {
        // fonte principal devolve um ponto na Europa: descarta e usa o OpenStreetMap
        respostas.put("cep", new Resp(200, cepJson("{\"longitude\":\"2.35\",\"latitude\":\"48.85\"}")));
        respostas.put("nom:postalcode", new Resp(200, "[{\"lat\":\"-23.55\",\"lon\":\"-46.63\"}]"));
        Localizacao l = cep.localizar("01001000");
        assertEquals(-23.55, l.lat(), 1e-7);
        // coordenadas (0,0) também não valem
        respostas.put("cep", new Resp(200, cepJson("{\"longitude\":\"0\",\"latitude\":\"0\"}")));
        assertEquals(-23.55, cep.localizar("01001000").lat(), 1e-7);
    }

    @Test
    void cep_naoEncontradoOuSemNenhumaCoordenada() {
        respostas.put("cep", new Resp(404, "{}"));
        assertThrows(ConsultaNaoEncontradaException.class, () -> cep.localizar("01001000"));

        respostas.put("cep", new Resp(200, cepJson("{}")));            // e o Nominatim não acha nada
        assertThrows(ConsultaNaoEncontradaException.class, () -> cep.localizar("01001000"));
    }

    @Test
    void cep_falhaDasFontesViraServicoExterno() {
        respostas.put("cep", new Resp(503, "{}"));
        assertThrows(ServicoExternoException.class, () -> cep.localizar("01001000"));

        respostas.put("cep", new Resp(200, cepJson("{}")));            // principal ok, Nominatim caiu em tudo
        respostas.put("nom:postalcode", new Resp(500, "{}"));
        respostas.put("nom:street", new Resp(500, "{}"));
        respostas.put("nom:city", new Resp(500, "{}"));
        assertThrows(ServicoExternoException.class, () -> cep.localizar("01001000"));
    }

    // ---------------------------------------------------------------- cache
    @Test
    void consulta_cacheiaSucessosMasNaoFalhas() {
        respostas.put("cnpj", new Resp(200, cnpjJson(1, false, 2, "ATIVA", 2062, 6201501)));
        ConsultaService svc = new ConsultaService(receita, cep,
                new IntegracoesProperties("x", "x", "ua", 500, 400, 0, 24, 30));

        svc.cnpj(CNPJ);
        svc.cnpj(CNPJ);
        svc.cnpj(Documentos.cnpj(CNPJ));
        assertEquals(1, chamadasReceita.get(), "consultas repetidas vêm do cache");

        String outro = Documentos.completarCnpj("223456780001");
        respostas.put("cnpj", new Resp(500, "{}"));
        assertThrows(ServicoExternoException.class, () -> svc.cnpj(outro));
        respostas.put("cnpj", new Resp(200, cnpjJson(3, false, 2, "ATIVA", 2062, 6201501)));
        assertEquals(PorteEmpresa.EPP, svc.cnpj(outro).porte(), "falha anterior não pode ter sido cacheada");
    }

    @Test
    void consulta_validaEntradaAntesDeChamarAFonte() {
        ConsultaService svc = new ConsultaService(receita, cep,
                new IntegracoesProperties("x", "x", "ua", 500, 400, 0, 24, 30));
        assertThrows(EntradaInvalidaException.class, () -> svc.cnpj("123"));
        assertThrows(EntradaInvalidaException.class, () -> svc.cep("abc"));
        assertEquals(0, chamadasReceita.get());
    }
}
