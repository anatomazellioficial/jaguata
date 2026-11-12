package com.jaguata.plataforma.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.jaguata.plataforma.exception.ServicoExternoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.function.Function;

/** Cliente HTTP JSON com timeouts e mapeamento uniforme de falhas para as exceções do domínio. */
final class HttpJson {
    private static final Logger log = LoggerFactory.getLogger(HttpJson.class);

    private HttpJson() {
    }

    static RestClient criar(String baseUrl, IntegracoesProperties p) {
        // HttpURLConnection: sem selector NIO (o HttpClient do JDK falha em alguns ambientes Windows/VPN)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(p.connectTimeoutMs());
        factory.setReadTimeout(p.readTimeoutMs());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("User-Agent", p.userAgent())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * GET que devolve o JSON, ou vazio em 404. Qualquer outra falha (4xx/5xx, timeout, DNS, JSON ruim)
     * vira {@link ServicoExternoException}. Nunca registra o corpo da resposta (pode ter dados de terceiros).
     */
    static Optional<JsonNode> get(RestClient client, String fonte, Function<RestClient, JsonNode> chamada) {
        try {
            return Optional.ofNullable(chamada.apply(client));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.warn("Falha ao consultar {}: HTTP {}", fonte, e.getStatusCode().value());
            throw new ServicoExternoException("Serviço de consulta (" + fonte + ") indisponível no momento.", e);
        } catch (ResourceAccessException e) {
            log.warn("Falha ao consultar {}: {}", fonte, e.getClass().getSimpleName());
            throw new ServicoExternoException("Serviço de consulta (" + fonte + ") não respondeu a tempo.", e);
        } catch (RestClientException e) {
            log.warn("Resposta ilegível de {}", fonte);
            throw new ServicoExternoException("Resposta inesperada do serviço de consulta (" + fonte + ").", e);
        }
    }

    static String texto(JsonNode n, String campo) {
        JsonNode v = n.path(campo);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("").trim();
    }
}
