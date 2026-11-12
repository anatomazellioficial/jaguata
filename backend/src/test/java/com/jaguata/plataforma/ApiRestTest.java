package com.jaguata.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Fluxo ponta a ponta pela API HTTP (com JWT) contra PostGIS real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegracoesStub.class)
@Testcontainers
class ApiRestTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static final String SENHA = "senha-forte-123";
    static final String ADMIN_EMAIL = "admin@teste.local";
    static final String ADMIN_SENHA = "girassol-azul-teste-42";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    // ---------- helpers ----------
    /** CNPJ válido; o prefixo "11" faz o stub da Receita devolver porte ME. */
    static String cnpj() {
        return TestCnpjs.com("11");
    }

    static String email() {
        return "u" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@teste.local";
    }

    ResultActions call(HttpMethod m, String url, String body, String token) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(m, url);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mvc.perform(b);
    }

    JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    String login(String email, String senha) throws Exception {
        return body(call(HttpMethod.POST, "/api/auth/login",
                "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha), null)
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    record Conta(String id, String email, String token) {
    }

    Conta registrarOng() throws Exception {
        String email = email();
        String id = body(call(HttpMethod.POST, "/api/auth/registro/ong", """
                {"email":"%s","senha":"%s","cnpj":"%s","nivelCapacidade":"NIVEL_1"}
                """.formatted(email, SENHA, TestCnpjs.com("55")), null).andExpect(status().isCreated())).get("entidadeId").asText();
        return new Conta(id, email, login(email, SENHA));
    }

    Conta ongVerificada() throws Exception {
        Conta ong = registrarOng();
        call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/verificar", null, login(ADMIN_EMAIL, ADMIN_SENHA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        return ong;
    }

    Conta registrarEmpresaME() throws Exception {
        String email = email();
        String id = body(call(HttpMethod.POST, "/api/auth/registro/empresa", """
                {"email":"%s","senha":"%s","cnpj":"%s"}
                """.formatted(email, SENHA, cnpj()), null).andExpect(status().isCreated())).get("entidadeId").asText();
        return new Conta(id, email, login(email, SENHA));
    }

    String candidatoJson(int idade) {
        return """
                {"nomeCompleto":"Maria","dataNascimento":"%s","cep":"01310100",
                 "modeloTrabalhoPref":"PRESENCIAL","tags":[{"categoria":"VULNERABILIDADE","tag":"PCD"}]}
                """.formatted(LocalDate.now().minusYears(idade).minusDays(1));
    }

    static final String VAGA_DIRETOR = """
            {"nivelCargo":"DIRETOR","tipoContrato":"FIXO","modeloTrabalho":"PRESENCIAL",
             "cepAtuacao":"01001000"}
            """;

    /** Vaga em Campinas: isola os testes que criam vários candidatos (o banco é compartilhado). */
    static final String VAGA_CAMPINAS = """
            {"nivelCargo":"DIRETOR","tipoContrato":"FIXO","modeloTrabalho":"PRESENCIAL","cepAtuacao":"13015904"}
            """;

    // ---------- fluxo feliz ----------
    @Test
    void fluxoCompletoComPapeis() throws Exception {
        Conta ong = ongVerificada();
        Conta empresa = registrarEmpresaME();

        call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/candidatos", candidatoJson(30), ong.token())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idade").value(30))
                .andExpect(jsonPath("$.tags[0].tag").value("PCD"));

        String vaga = body(call(HttpMethod.POST, "/api/empresas/" + empresa.id() + "/vagas", VAGA_DIRETOR, empresa.token())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nivelCargo").value("DIRETOR"))
                .andExpect(jsonPath("$.tituloExibicao").value("Coordenador/Gerente"))).get("id").asText();

        call(HttpMethod.POST, "/api/vagas/" + vaga + "/matches/sugestoes?raioKm=10", null, empresa.token())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tituloVaga").value("Coordenador/Gerente"));

        // a ONG enxerga a fila e a envia
        String match = body(call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/matches", null, ong.token())
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))).get(0).get("id").asText();

        // empresa não pode "enviar" (é papel da ONG); ONG não pode contratar
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"ENVIADO\"}", empresa.token())
                .andExpect(status().isForbidden());
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"ENVIADO\"}", ong.token())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENVIADO"));
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"ENTREVISTA\"}", ong.token())
                .andExpect(status().isForbidden());
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"CONTRATADO\"}", empresa.token())
                .andExpect(status().isUnprocessableEntity());   // pulou a entrevista: regra do funil
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"ENTREVISTA\"}", empresa.token())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENTREVISTA"));
    }

    // ---------- autenticação ----------
    @Test
    void semTokenRetorna401() throws Exception {
        call(HttpMethod.GET, "/api/cardapio/cargos", null, null).andExpect(status().isUnauthorized());
        call(HttpMethod.GET, "/api/vagas/00000000-0000-0000-0000-000000000000", null, null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAdulteradoOuLixoRetorna401() throws Exception {
        Conta e = registrarEmpresaME();
        call(HttpMethod.GET, "/api/cardapio/cargos", null, e.token() + "x").andExpect(status().isUnauthorized());
        call(HttpMethod.GET, "/api/cardapio/cargos", null, "lixo").andExpect(status().isUnauthorized());
    }

    @Test
    void loginComSenhaErradaOuEmailInexistente401() throws Exception {
        Conta e = registrarEmpresaME();
        call(HttpMethod.POST, "/api/auth/login",
                "{\"email\":\"%s\",\"senha\":\"errada-errada\"}".formatted(e.email()), null)
                .andExpect(status().isUnauthorized());
        call(HttpMethod.POST, "/api/auth/login",
                "{\"email\":\"nao-existe@teste.local\",\"senha\":\"qualquer-coisa\"}", null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emailEhCaseInsensitiveEUnico() throws Exception {
        Conta e = registrarEmpresaME();
        login(e.email().toUpperCase(), SENHA);
        call(HttpMethod.POST, "/api/auth/registro/empresa", """
                {"email":"%s","senha":"%s","cnpj":"%s"}
                """.formatted(e.email().toUpperCase(), SENHA, cnpj()), null)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void senhaCurtaEhRejeitada() throws Exception {
        call(HttpMethod.POST, "/api/auth/registro/empresa", """
                {"email":"%s","senha":"123","cnpj":"%s"}
                """.formatted(email(), cnpj()), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.senha").exists());
    }

    @Test
    void cnpjDuplicadoNoRegistroEhRejeitado() throws Exception {
        String c = cnpj();
        String corpo = """
                {"email":"%s","senha":"%s","cnpj":"%s"}
                """;
        call(HttpMethod.POST, "/api/auth/registro/empresa", corpo.formatted(email(), SENHA, c), null)
                .andExpect(status().isCreated());
        call(HttpMethod.POST, "/api/auth/registro/empresa", corpo.formatted(email(), SENHA, c), null)
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------- autorização ----------
    @Test
    void empresaNaoAcessaRecursosDeOutraEmpresa() throws Exception {
        Conta a = registrarEmpresaME();
        Conta b = registrarEmpresaME();
        String vagaA = body(call(HttpMethod.POST, "/api/empresas/" + a.id() + "/vagas", VAGA_DIRETOR, a.token())
                .andExpect(status().isCreated())).get("id").asText();

        call(HttpMethod.GET, "/api/empresas/" + a.id(), null, b.token()).andExpect(status().isForbidden());
        call(HttpMethod.POST, "/api/empresas/" + a.id() + "/vagas", VAGA_DIRETOR, b.token())
                .andExpect(status().isForbidden());
        call(HttpMethod.GET, "/api/vagas/" + vagaA, null, b.token()).andExpect(status().isForbidden());
        call(HttpMethod.POST, "/api/vagas/" + vagaA + "/matches/sugestoes", null, b.token())
                .andExpect(status().isForbidden());
        call(HttpMethod.GET, "/api/vagas/" + vagaA, null, a.token()).andExpect(status().isOk());
    }

    @Test
    void ongNaoCriaVagaNemCadastraCandidatoEmOutraOng() throws Exception {
        Conta ong1 = registrarOng();
        Conta ong2 = registrarOng();
        Conta empresa = registrarEmpresaME();

        call(HttpMethod.POST, "/api/empresas/" + empresa.id() + "/vagas", VAGA_DIRETOR, ong1.token())
                .andExpect(status().isForbidden());
        call(HttpMethod.POST, "/api/ongs/" + ong2.id() + "/candidatos", candidatoJson(30), ong1.token())
                .andExpect(status().isForbidden());
    }

    @Test
    void apenasAdminVerificaOng() throws Exception {
        Conta ong = registrarOng();
        Conta empresa = registrarEmpresaME();
        call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/verificar", null, ong.token())
                .andExpect(status().isForbidden());
        call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/verificar", null, empresa.token())
                .andExpect(status().isForbidden());
        call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/verificar", null, login(ADMIN_EMAIL, ADMIN_SENHA))
                .andExpect(status().isOk());
    }

    // ---------- regras de negócio via API ----------
    @Test
    void menorDeIdadeERejeitado() throws Exception {
        Conta ong = registrarOng();
        call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/candidatos", candidatoJson(17), ong.token())
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cargoForaDoCardapioERejeitado() throws Exception {
        Conta empresa = registrarEmpresaME();
        call(HttpMethod.POST, "/api/empresas/" + empresa.id() + "/vagas", """
                {"nivelCargo":"GURU","tipoContrato":"FIXO","modeloTrabalho":"REMOTO",
                 "cepAtuacao":"01001000"}
                """, empresa.token()).andExpect(status().isBadRequest());
    }

    @Test
    void cardapioListaTodosOsCargos() throws Exception {
        Conta empresa = registrarEmpresaME();
        call(HttpMethod.GET, "/api/cardapio/cargos", null, empresa.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[0].codigo").value("APRENDIZ"));
    }

    // ---------- Receita Federal e CEP ----------
    static String reg(String email, String cnpj) {
        return """
                {"email":"%s","senha":"%s","cnpj":"%s"}
                """.formatted(email, SENHA, cnpj);
    }

    @Test
    void cadastroUsaPorteERazaoSocialDaReceitaNuncaDoCliente() throws Exception {
        String email = email();
        // o cliente tenta impor porte, razão social, CNAE e coordenadas: tudo é ignorado
        String id = body(call(HttpMethod.POST, "/api/auth/registro/empresa", """
                {"email":"%s","senha":"%s","cnpj":"%s",
                 "porteReceita":"DEMAIS","razaoSocial":"Fraude SA","cnaePrincipal":"0000000","lat":1,"lng":1}
                """.formatted(email, SENHA, TestCnpjs.com("22")), null).andExpect(status().isCreated())).get("entidadeId").asText();
        String token = login(email, SENHA);
        call(HttpMethod.GET, "/api/empresas/" + id, null, token)
                .andExpect(jsonPath("$.porteReceita").value("EPP"))
                .andExpect(jsonPath("$.cnaePrincipal").value("6201501"))
                .andExpect(jsonPath("$.razaoSocial").value(org.hamcrest.Matchers.startsWith("Empresa Teste")));
    }

    @Test
    void registroRecusaCnpjInvalidoInativoInexistenteEForaDoAr() throws Exception {
        call(HttpMethod.POST, "/api/auth/registro/empresa", reg(email(), "12345678000199"), null)
                .andExpect(status().isBadRequest());                       // dígito verificador errado
        call(HttpMethod.POST, "/api/auth/registro/empresa", reg(email(), TestCnpjs.com("99")), null)
                .andExpect(status().isUnprocessableEntity())               // situação BAIXADA
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("baixada")));
        call(HttpMethod.POST, "/api/auth/registro/empresa", reg(email(), TestCnpjs.com("88")), null)
                .andExpect(status().isUnprocessableEntity());              // não existe na Receita
        call(HttpMethod.POST, "/api/auth/registro/empresa", reg(email(), TestCnpjs.com("77")), null)
                .andExpect(status().isServiceUnavailable())                // Receita fora do ar
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void registroDeOngUsaDadosDaReceita() throws Exception {
        Conta ong = registrarOng();
        call(HttpMethod.GET, "/api/admin/ongs?verificada=false", null, login(ADMIN_EMAIL, ADMIN_SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + ong.id() + "')].razaoSocial")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.startsWith("Associação Teste"))));
    }

    @Test
    void consultaPublicaDeCnpjDevolveSoDadosPublicos() throws Exception {
        call(HttpMethod.GET, "/api/consulta/cnpj/" + TestCnpjs.com("33"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porte").value("MEI"))
                .andExpect(jsonPath("$.ativa").value(true))
                .andExpect(jsonPath("$.razaoSocial").exists())
                .andExpect(jsonPath("$.qsa").doesNotExist())
                .andExpect(jsonPath("$.socios").doesNotExist());
        call(HttpMethod.GET, "/api/consulta/cnpj/11111111111111", null, null).andExpect(status().isBadRequest());
        call(HttpMethod.GET, "/api/consulta/cnpj/" + TestCnpjs.com("88"), null, null).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/consulta/cnpj/" + TestCnpjs.com("77"), null, null).andExpect(status().isServiceUnavailable());
    }

    @Test
    void consultaPublicaDeCepDevolveEnderecoECoordenadas() throws Exception {
        call(HttpMethod.GET, "/api/consulta/cep/01001000", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logradouro").value("Praça da Sé"))
                .andExpect(jsonPath("$.lat").value(-23.5505))
                .andExpect(jsonPath("$.precisao").value("CEP"));
        call(HttpMethod.GET, "/api/consulta/cep/0100", null, null).andExpect(status().isBadRequest());
        call(HttpMethod.GET, "/api/consulta/cep/00000000", null, null).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/consulta/cep/99999999", null, null).andExpect(status().isServiceUnavailable());
    }

    @Test
    void consultaNaoAceitaMetodosQueMudamEstado() throws Exception {
        call(HttpMethod.POST, "/api/consulta/cnpj/" + TestCnpjs.com("11"), "{}", null).andExpect(status().isUnauthorized());
    }

    @Test
    void cepInexistenteOuInvalidoBarraVagaECandidato() throws Exception {
        Conta empresa = registrarEmpresaME();
        Conta ong = registrarOng();
        call(HttpMethod.POST, "/api/empresas/" + empresa.id() + "/vagas", """
                {"nivelCargo":"ANALISTA","tipoContrato":"FIXO","modeloTrabalho":"REMOTO","cepAtuacao":"00000000"}
                """, empresa.token()).andExpect(status().isNotFound());
        call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/candidatos",
                candidatoJson(30).replace("01310100", "abc"), ong.token()).andExpect(status().isBadRequest());
    }

    // ---------- área admin ----------
    @Test
    void adminListaPendentesVerificaERevoga() throws Exception {
        String admin = login(ADMIN_EMAIL, ADMIN_SENHA);
        Conta ong = registrarOng();

        String pendentes = call(HttpMethod.GET, "/api/admin/ongs?verificada=false", null, admin)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(pendentes.contains(ong.id()));

        call(HttpMethod.POST, "/api/admin/ongs/" + ong.id() + "/verificar", null, admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        String verificadas = call(HttpMethod.GET, "/api/admin/ongs?verificada=true", null, admin)
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(verificadas.contains(ong.id()));

        call(HttpMethod.POST, "/api/admin/ongs/" + ong.id() + "/revogar", null, admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    void resumoAdminTemContagens() throws Exception {
        registrarOng();
        call(HttpMethod.GET, "/api/admin/resumo", null, login(ADMIN_EMAIL, ADMIN_SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ongs").isNumber())
                .andExpect(jsonPath("$.ongsPendentes").isNumber())
                .andExpect(jsonPath("$.matchesPorStatus.SUGERIDO").isNumber());
    }

    @Test
    void rotasAdminSaoProibidasParaEmpresaEOng() throws Exception {
        Conta empresa = registrarEmpresaME();
        Conta ong = registrarOng();
        for (Conta c : new Conta[]{empresa, ong}) {
            call(HttpMethod.GET, "/api/admin/ongs", null, c.token()).andExpect(status().isForbidden());
            call(HttpMethod.GET, "/api/admin/resumo", null, c.token()).andExpect(status().isForbidden());
            call(HttpMethod.POST, "/api/admin/ongs/" + ong.id() + "/revogar", null, c.token())
                    .andExpect(status().isForbidden());
        }
        call(HttpMethod.GET, "/api/admin/ongs", null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void vagaInexistenteRetorna404ParaAdmin() throws Exception {
        call(HttpMethod.GET, "/api/vagas/00000000-0000-0000-0000-000000000000", null,
                login(ADMIN_EMAIL, ADMIN_SENHA)).andExpect(status().isNotFound());
    }

    // ---------- contratação fecha a vaga (API) ----------
    @Test
    void contratarFechaAVagaEBarraCandidatoJaContratado() throws Exception {
        Conta ong = ongVerificada();
        Conta empresa = registrarEmpresaME();
        for (String nome : new String[]{"Ana Lima", "Bia Costa"}) {
            call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/candidatos",
                    candidatoJson(30).replace("Maria", nome).replace("01310100", "13015904"), ong.token()).andExpect(status().isCreated());
        }
        String vagaA = body(call(HttpMethod.POST, "/api/empresas/" + empresa.id() + "/vagas", VAGA_CAMPINAS, empresa.token())
                .andExpect(status().isCreated())).get("id").asText();

        JsonNode sugeridos = body(call(HttpMethod.POST, "/api/vagas/" + vagaA + "/matches/sugestoes?raioKm=10", null, empresa.token())
                .andExpect(status().isCreated()).andExpect(jsonPath("$", hasSize(2))));
        String primeiro = sugeridos.get(0).get("id").asText();
        String segundo = sugeridos.get(1).get("id").asText();
        String candidatoContratado = sugeridos.get(0).get("candidatoId").asText();

        // a ONG envia os dois; a empresa entrevista e contrata o primeiro
        for (String id : new String[]{primeiro, segundo}) {
            call(HttpMethod.PATCH, "/api/matches/" + id + "/status", "{\"status\":\"ENVIADO\"}", ong.token()).andExpect(status().isOk());
        }
        call(HttpMethod.PATCH, "/api/matches/" + primeiro + "/status", "{\"status\":\"ENTREVISTA\"}", empresa.token()).andExpect(status().isOk());
        call(HttpMethod.PATCH, "/api/matches/" + primeiro + "/status", "{\"status\":\"CONTRATADO\"}", empresa.token())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONTRATADO"));

        // a vaga foi preenchida
        call(HttpMethod.GET, "/api/vagas/" + vagaA, null, empresa.token())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PREENCHIDA"));
        // a pendência do outro candidato foi encerrada e não avança mais
        call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/matches?status=RECUSADO", null, ong.token())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(segundo));
        call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/matches?status=ENVIADO", null, ong.token())
                .andExpect(jsonPath("$", hasSize(0)));
        // vaga fechada não aceita nova busca
        call(HttpMethod.POST, "/api/vagas/" + vagaA + "/matches/sugestoes?raioKm=10", null, empresa.token())
                .andExpect(status().isCreated()).andExpect(jsonPath("$", hasSize(0)));

        // o candidato contratado não é sugerido para outra vaga
        String vagaB = body(call(HttpMethod.POST, "/api/empresas/" + empresa.id() + "/vagas", VAGA_CAMPINAS, empresa.token())
                .andExpect(status().isCreated())).get("id").asText();
        JsonNode paraB = body(call(HttpMethod.POST, "/api/vagas/" + vagaB + "/matches/sugestoes?raioKm=10", null, empresa.token())
                .andExpect(status().isCreated()));
        for (JsonNode m : paraB) {
            org.junit.jupiter.api.Assertions.assertNotEquals(candidatoContratado, m.get("candidatoId").asText());
        }
    }
}
