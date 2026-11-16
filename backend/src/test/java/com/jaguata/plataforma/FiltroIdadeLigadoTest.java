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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Com {@code app.matching.filtro-idade=true} (só depois do aval do jurídico) o filtro de idade funciona.
 * O padrão da instalação é DESLIGADO: veja NovosRecursosTest.
 */
@SpringBootTest(properties = "app.matching.filtro-idade=true")
@AutoConfigureMockMvc
@Import(IntegracoesStub.class)
@Testcontainers
class FiltroIdadeLigadoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static final String SENHA = "senha-forte-123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturaEmails emails;

    ResultActions call(HttpMethod m, String url, String body, String token) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(m, url);
        if (body != null && url.startsWith("/api/auth/registro/")) body = body.replaceFirst("\\{", "{\"aceiteTermos\":true,");
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mvc.perform(b);
    }

    JsonNode corpo(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    String login(String email, String senha) throws Exception {
        return corpo(call(HttpMethod.POST, "/api/auth/login", "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha), null)
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    String novoEmail() {
        return "i" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@teste.local";
    }

    @Test
    void filtraPorFaixaDeIdadeQuandoLigado() throws Exception {
        String emailOng = novoEmail();
        String ongId = corpo(call(HttpMethod.POST, "/api/auth/registro/ong", """
                {"email":"%s","senha":"%s","cnpj":"%s","nivelCapacidade":"NIVEL_1"}
                """.formatted(emailOng, SENHA, TestCnpjs.com("55")), null).andExpect(status().isCreated())).get("entidadeId").asText();
        call(HttpMethod.POST, "/api/auth/email/verificar", "{\"token\":\"" + emails.ultimoToken(emailOng, "verificar-email") + "\"}", null)
                .andExpect(status().isNoContent());
        call(HttpMethod.POST, "/api/ongs/" + ongId + "/verificar", null, login("admin@teste.local", "girassol-azul-teste-42")).andExpect(status().isOk());
        String tokenOng = login(emailOng, SENHA);

        String emailEmp = novoEmail();
        String empId = corpo(call(HttpMethod.POST, "/api/auth/registro/empresa", """
                {"email":"%s","senha":"%s","cnpj":"%s"}
                """.formatted(emailEmp, SENHA, TestCnpjs.com("11")), null).andExpect(status().isCreated())).get("entidadeId").asText();
        call(HttpMethod.POST, "/api/auth/email/verificar", "{\"token\":\"" + emails.ultimoToken(emailEmp, "verificar-email") + "\"}", null)
                .andExpect(status().isNoContent());
        String tokenEmp = login(emailEmp, SENHA);

        for (int idade : new int[]{25, 45, 58}) {
            call(HttpMethod.POST, "/api/ongs/" + ongId + "/candidatos", """
                    {"nomeCompleto":"Idade %d","dataNascimento":"%s","cep":"13015904","modeloTrabalhoPref":"PRESENCIAL","consentimentoConfirmado":true}
                    """.formatted(idade, LocalDate.now().minusYears(idade).minusDays(30)), tokenOng).andExpect(status().isCreated());
        }
        call(HttpMethod.GET, "/api/cardapio/recursos", null, tokenEmp).andExpect(jsonPath("$.filtroIdade").value(true));

        // cada busca em uma vaga nova (a busca grava as sugestões)
        assertEquals(List.of("Idade 45", "Idade 58"), buscar(empId, tokenEmp, "idadeMin=40"));
        assertEquals(List.of("Idade 25", "Idade 45"), buscar(empId, tokenEmp, "idadeMax=50"));
        assertEquals(List.of("Idade 45"), buscar(empId, tokenEmp, "idadeMin=40&idadeMax=50"));

        // faixas inválidas
        String v = vaga(empId, tokenEmp);
        call(HttpMethod.POST, "/api/vagas/" + v + "/matches/sugestoes?raioKm=5&idadeMin=60&idadeMax=40", null, tokenEmp)
                .andExpect(status().isUnprocessableEntity());
        call(HttpMethod.POST, "/api/vagas/" + v + "/matches/sugestoes?raioKm=5&idadeMin=17", null, tokenEmp)
                .andExpect(status().isUnprocessableEntity());
    }

    String vaga(String empId, String token) throws Exception {
        return corpo(call(HttpMethod.POST, "/api/empresas/" + empId + "/vagas", """
                {"nivelCargo":"ANALISTA","tipoContrato":"FIXO","modeloTrabalho":"PRESENCIAL","cepAtuacao":"13015904"}
                """, token).andExpect(status().isCreated())).get("id").asText();
    }

    List<String> buscar(String empId, String token, String filtro) throws Exception {
        String v = vaga(empId, token);
        JsonNode r = corpo(call(HttpMethod.POST, "/api/vagas/" + v + "/matches/sugestoes?raioKm=15&limite=200&" + filtro, null, token)
                .andExpect(status().isCreated()));
        List<String> nomes = new ArrayList<>();
        r.forEach(m -> {
            String n = m.get("candidatoNome").asText();
            if (n.startsWith("Idade ")) nomes.add(n);
        });
        nomes.sort(String::compareTo);
        return nomes;
    }
}
