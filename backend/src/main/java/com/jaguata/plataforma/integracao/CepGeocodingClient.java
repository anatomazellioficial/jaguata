package com.jaguata.plataforma.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.jaguata.plataforma.exception.ConsultaNaoEncontradaException;
import com.jaguata.plataforma.exception.ServicoExternoException;
import com.jaguata.plataforma.integracao.Localizacao.Precisao;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * CEP → endereço e coordenadas. Fonte principal: BrasilAPI (v2, já traz coordenadas na maioria dos casos).
 * Reserva, nesta ordem, pelo Nominatim/OpenStreetMap: por CEP, por rua+cidade e por cidade. A precisão
 * obtida é informada em {@link Localizacao#precisao()}.
 * Só trafegam CEP e nomes de logradouro/cidade (dados públicos); nenhum dado de usuário vai para essas fontes.
 */
@Component
public class CepGeocodingClient implements CepClient {

    private final RestClient brasil;
    private final RestClient nominatim;
    private final long minIntervalMs;
    private long ultimaChamadaNominatim;

    public CepGeocodingClient(IntegracoesProperties props) {
        this.brasil = HttpJson.criar(props.brasilApiUrl(), props);
        this.nominatim = HttpJson.criar(props.nominatimUrl(), props);
        this.minIntervalMs = props.nominatimMinIntervalMs();
    }

    @Override
    public Localizacao localizar(String cep) {
        JsonNode n = HttpJson.get(brasil, "CEP",
                        c -> c.get().uri("/api/cep/v2/{cep}", cep).retrieve().body(JsonNode.class))
                .orElseThrow(() -> new ConsultaNaoEncontradaException("CEP não encontrado."));

        String logradouro = HttpJson.texto(n, "street");
        String bairro = HttpJson.texto(n, "neighborhood");
        String cidade = HttpJson.texto(n, "city");
        String uf = HttpJson.texto(n, "state");

        JsonNode coord = n.path("location").path("coordinates");
        double[] p = ponto(HttpJson.texto(coord, "latitude"), HttpJson.texto(coord, "longitude"));
        if (p != null) return new Localizacao(cep, logradouro, bairro, cidade, uf, p[0], p[1], Precisao.CEP);

        // sem coordenadas na fonte principal: tenta o OpenStreetMap, do mais ao menos preciso
        record Tentativa(Precisao precisao, Function<UriBuilder, UriBuilder> consulta) {
        }
        List<Tentativa> tentativas = new ArrayList<>();
        tentativas.add(new Tentativa(Precisao.CEP, b -> b.queryParam("postalcode", cep).queryParam("country", "Brazil")));
        if (!logradouro.isEmpty() && !cidade.isEmpty()) {
            tentativas.add(new Tentativa(Precisao.RUA, b -> b.queryParam("street", logradouro)
                    .queryParam("city", cidade).queryParam("state", uf).queryParam("country", "Brazil")));
        }
        if (!cidade.isEmpty()) {
            tentativas.add(new Tentativa(Precisao.CIDADE, b -> b.queryParam("city", cidade)
                    .queryParam("state", uf).queryParam("country", "Brazil")));
        }

        ServicoExternoException falha = null;
        for (Tentativa t : tentativas) {
            try {
                double[] q = buscarNominatim(t.consulta());
                if (q != null) return new Localizacao(cep, logradouro, bairro, cidade, uf, q[0], q[1], t.precisao());
            } catch (ServicoExternoException e) {
                falha = e;
            }
        }
        if (falha != null) throw falha;
        throw new ConsultaNaoEncontradaException("Não foi possível localizar as coordenadas deste CEP.");
    }

    private double[] buscarNominatim(Function<UriBuilder, UriBuilder> params) {
        esperarIntervaloMinimo();
        JsonNode r = HttpJson.get(nominatim, "geocodificação", c -> c.get()
                .uri(b -> params.apply(b.path("/search").queryParam("format", "jsonv2").queryParam("limit", 1)).build())
                .retrieve().body(JsonNode.class)).orElse(null);
        if (r == null || !r.isArray() || r.isEmpty()) return null;
        return ponto(HttpJson.texto(r.get(0), "lat"), HttpJson.texto(r.get(0), "lon"));
    }

    /** Política de uso do Nominatim público: no máximo ~1 requisição por segundo. */
    private synchronized void esperarIntervaloMinimo() {
        long espera = ultimaChamadaNominatim + minIntervalMs - System.currentTimeMillis();
        if (espera > 0) {
            try {
                Thread.sleep(espera);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        ultimaChamadaNominatim = System.currentTimeMillis();
    }

    /** Converte e valida: números, dentro do território brasileiro e diferente de (0,0). */
    static double[] ponto(String lat, String lng) {
        try {
            double la = Double.parseDouble(lat), lo = Double.parseDouble(lng);
            boolean noBrasil = la >= -34.0 && la <= 6.0 && lo >= -75.0 && lo <= -28.0;
            return noBrasil ? new double[]{la, lo} : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
