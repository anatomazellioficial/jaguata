package com.jaguata.plataforma.dto;

import com.jaguata.plataforma.domain.*;
import com.jaguata.plataforma.domain.enums.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Payloads de saída. Nunca expõem entidades JPA (evita lazy loading e vazamento de campos). */
public final class Responses {
    private Responses() {
    }

    public record EmpresaResponse(UUID id, String cnpj, String razaoSocial, String cnaePrincipal,
                                  PorteEmpresa porteReceita) {
        public static EmpresaResponse from(Empresa e) {
            return new EmpresaResponse(e.getId(), e.getCnpj(), e.getRazaoSocial(), e.getCnaePrincipal(),
                    e.getPorteReceita());
        }
    }

    public record OngResponse(UUID id, String cnpj, String razaoSocial, String cnaePrincipal,
                              NivelCapacidade nivelCapacidade, boolean verified) {
        public static OngResponse from(Ong o) {
            return new OngResponse(o.getId(), o.getCnpj(), o.getRazaoSocial(), o.getCnaePrincipal(),
                    o.getNivelCapacidade(), o.isVerified());
        }
    }

    public record TagResponse(CategoriaTag categoria, String tag) {
    }

    public record CandidatoResponse(UUID id, UUID ongId, String nomeCompleto, int idade,
                                    ModeloTrabalho modeloTrabalhoPref, List<TagResponse> tags) {
        public static CandidatoResponse from(Candidato c) {
            return new CandidatoResponse(c.getId(), c.getOng().getId(), c.getNomeCompleto(), c.getIdade(),
                    c.getModeloTrabalhoPref(),
                    c.getTags().stream().map(t -> new TagResponse(t.getCategoria(), t.getTag())).toList());
        }
    }

    /** Visão da EMPRESA: mostra o cargo escolhido e o título que a ONG enxergará. */
    public record VagaResponse(UUID id, UUID empresaId, NivelCargo nivelCargo, String tituloExibicao,
                               TipoContrato tipoContrato, ModeloTrabalho modeloTrabalho,
                               String cepAtuacao, StatusVaga status) {
        public static VagaResponse from(Vaga v) {
            return new VagaResponse(v.getId(), v.getEmpresa().getId(), v.getNivelCargo(), v.getTituloExibicao(),
                    v.getTipoContrato(), v.getModeloTrabalho(), v.getCepAtuacao(), v.getStatus());
        }
    }

    /** Visão da ONG: apenas o título já traduzido pela Matriz de Complexidade. */
    public record MatchResponse(UUID id, UUID vagaId, String tituloVaga, UUID candidatoId,
                                String candidatoNome, UUID ongId, BigDecimal matchScore, StatusMatch status) {
        public static MatchResponse from(Match m) {
            return new MatchResponse(m.getId(), m.getVaga().getId(), m.getVaga().getTituloExibicao(),
                    m.getCandidato().getId(), m.getCandidato().getNomeCompleto(), m.getOng().getId(),
                    m.getMatchScore(), m.getStatus());
        }
    }

    public record AdminOngResponse(UUID id, String cnpj, String razaoSocial, String cnaePrincipal,
                                   NivelCapacidade nivelCapacidade, String cep, boolean verified,
                                   java.time.OffsetDateTime createdAt) {
        public static AdminOngResponse from(Ong o) {
            return new AdminOngResponse(o.getId(), o.getCnpj(), o.getRazaoSocial(), o.getCnaePrincipal(),
                    o.getNivelCapacidade(), o.getCep(), o.isVerified(), o.getCreatedAt());
        }
    }

    public record ResumoResponse(long empresas, long ongs, long ongsVerificadas, long ongsPendentes,
                                 long candidatos, long vagasAbertas,
                                 java.util.Map<StatusMatch, Long> matchesPorStatus) {
    }

    /** Dados públicos do CNPJ (sem quadro societário) para pré-visualização no cadastro. */
    public record ConsultaCnpjResponse(String cnpj, String razaoSocial, String nomeFantasia, String cnaePrincipal,
                                       PorteEmpresa porte, boolean ativa, String situacao, String cep,
                                       String logradouro, String bairro, String municipio, String uf,
                                       String naturezaJuridica, boolean entidadeSemFinsLucrativos) {
        public static ConsultaCnpjResponse from(com.jaguata.plataforma.integracao.DadosCnpj d) {
            return new ConsultaCnpjResponse(d.cnpj(), d.razaoSocial(), d.nomeFantasia(), d.cnaePrincipal(), d.porte(),
                    d.ativa(), d.situacao(), d.cep(), d.logradouro(), d.bairro(), d.municipio(), d.uf(),
                    d.naturezaJuridica(), d.entidadeSemFinsLucrativos());
        }
    }

    public record ConsultaCepResponse(String cep, String logradouro, String bairro, String cidade, String uf,
                                      double lat, double lng, String precisao) {
        public static ConsultaCepResponse from(com.jaguata.plataforma.integracao.Localizacao l) {
            return new ConsultaCepResponse(l.cep(), l.logradouro(), l.bairro(), l.cidade(), l.uf(), l.lat(), l.lng(),
                    l.precisao().name());
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
    }

    /** Resultado do registro: id da empresa ou da ONG criada (o login é feito em seguida). */
    public record RegistroResponse(UUID entidadeId, String email, PapelUsuario papel) {
    }

    public record CargoResponse(NivelCargo codigo, String rotulo) {
    }
}
