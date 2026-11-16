package com.jaguata.plataforma.service;

import com.jaguata.plataforma.conta.AuditoriaService;
import com.jaguata.plataforma.conta.SessaoService;
import com.jaguata.plataforma.conta.Termos;
import com.jaguata.plataforma.conta.TotpService;
import com.jaguata.plataforma.domain.Empresa;
import com.jaguata.plataforma.domain.Ong;
import com.jaguata.plataforma.domain.Usuario;
import com.jaguata.plataforma.domain.enums.NivelCapacidade;
import com.jaguata.plataforma.domain.enums.PapelUsuario;
import com.jaguata.plataforma.exception.ConsultaNaoEncontradaException;
import com.jaguata.plataforma.exception.CredenciaisInvalidasException;
import com.jaguata.plataforma.exception.EmailNaoVerificadoException;
import com.jaguata.plataforma.exception.EntradaInvalidaException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.exception.SessaoInvalidaException;
import com.jaguata.plataforma.exception.TentativasExcedidasException;
import com.jaguata.plataforma.exception.TotpRequeridoException;
import com.jaguata.plataforma.integracao.ConsultaService;
import com.jaguata.plataforma.integracao.DadosCnpj;
import com.jaguata.plataforma.integracao.Documentos;
import com.jaguata.plataforma.integracao.Localizacao;
import com.jaguata.plataforma.repository.UsuarioRepository;
import com.jaguata.plataforma.security.JwtService;
import com.jaguata.plataforma.security.LoginAttemptService;
import com.jaguata.plataforma.security.SenhaPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Locale;

@Service
public class AuthService {

    /** Par de tokens devolvido no login e na renovação. */
    public record Tokens(String accessToken, long expiresInSeconds, String refreshToken, long refreshExpiresInSeconds) {
    }

    /** Hash descartável para igualar o tempo de resposta quando o e-mail não existe. */
    private final String hashFalso;

    private final UsuarioRepository usuarios;
    private final EmpresaService empresas;
    private final OngService ongs;
    private final PasswordEncoder encoder;
    private final LoginAttemptService tentativas;
    private final ConsultaService consulta;
    private final TransactionTemplate tx;
    private final ContaService contas;
    private final SessaoService sessoes;
    private final JwtService jwt;
    private final AuditoriaService auditoria;
    private final TotpService totp;

    public AuthService(UsuarioRepository usuarios, EmpresaService empresas, OngService ongs,
                       PasswordEncoder encoder, LoginAttemptService tentativas, ConsultaService consulta,
                       TransactionTemplate tx, ContaService contas, SessaoService sessoes, JwtService jwt,
                       AuditoriaService auditoria, TotpService totp) {
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.ongs = ongs;
        this.encoder = encoder;
        this.tentativas = tentativas;
        this.consulta = consulta;
        this.tx = tx;
        this.contas = contas;
        this.sessoes = sessoes;
        this.jwt = jwt;
        this.auditoria = auditoria;
        this.totp = totp;
        this.hashFalso = encoder.encode("senha-inexistente");
    }

    // ------------------------------------------------------------------ cadastro
    /**
     * Cadastro de empresa. Razão social, CNAE e porte vêm da Receita Federal e o endereço/coordenadas, do CEP
     * da sede; o cliente informa apenas o CNPJ. As consultas externas rodam ANTES da transação, para não
     * segurar conexão do banco enquanto aguardam a rede. A conta nasce com e-mail NÃO verificado.
     */
    public Usuario registrarEmpresa(String email, String senha, String cnpjBruto) {
        String e = normalizar(email);
        SenhaPolicy.validar(senha, e);
        garantirEmailLivre(e);
        String cnpj = Documentos.cnpj(cnpjBruto);
        if (empresas.cnpjCadastrado(cnpj)) throw new RegraNegocioException("Já existe empresa cadastrada com este CNPJ.");

        DadosCnpj dados = consultarAtivo(cnpj);
        Localizacao loc = localizarSede(dados);
        Usuario u = tx.execute(s -> {
            Empresa empresa = empresas.criar(dados, loc);
            Usuario novo = novo(e, senha, PapelUsuario.EMPRESA);
            novo.setEmpresaId(empresa.getId());
            return usuarios.save(novo);
        });
        aposCadastro(u);
        return u;
    }

    /** A ONG nasce não verificada; só participa de matches após verificação por um ADMIN. */
    public Usuario registrarOng(String email, String senha, String cnpjBruto, NivelCapacidade nivel) {
        String e = normalizar(email);
        SenhaPolicy.validar(senha, e);
        garantirEmailLivre(e);
        String cnpj = Documentos.cnpj(cnpjBruto);
        if (ongs.cnpjCadastrado(cnpj)) throw new RegraNegocioException("Já existe ONG cadastrada com este CNPJ.");

        DadosCnpj dados = consultarAtivo(cnpj);
        Localizacao loc = localizarSede(dados);
        Usuario u = tx.execute(s -> {
            Ong ong = ongs.criar(dados, loc, nivel);
            Usuario novo = novo(e, senha, PapelUsuario.ONG);
            novo.setOngId(ong.getId());
            return usuarios.save(novo);
        });
        aposCadastro(u);
        return u;
    }

    private void aposCadastro(Usuario u) {
        auditoria.registrar(u.getId(), u.getPapel().name(), "REGISTRO", "usuario", u.getId(),
                "termos=" + u.getTermosVersao());
        contas.enviarVerificacao(u);
    }

    private DadosCnpj consultarAtivo(String cnpj) {
        DadosCnpj d;
        try {
            d = consulta.cnpj(cnpj);
        } catch (ConsultaNaoEncontradaException ex) {
            throw new RegraNegocioException("CNPJ não encontrado na Receita Federal.");
        }
        if (!d.ativa()) {
            throw new RegraNegocioException("O CNPJ está com situação cadastral " + d.situacao().toLowerCase(Locale.ROOT)
                    + " na Receita Federal. Só é possível cadastrar CNPJs ativos.");
        }
        return d;
    }

    private Localizacao localizarSede(DadosCnpj d) {
        try {
            return consulta.cep(d.cep());
        } catch (ConsultaNaoEncontradaException | EntradaInvalidaException ex) {
            throw new RegraNegocioException("Não foi possível localizar o endereço (CEP da sede) deste CNPJ.");
        }
    }

    /** O administrador inicial é criado já com e-mail verificado (vem de variável de ambiente, não de cadastro). */
    @Transactional
    public Usuario criarAdminSeAusente(String email, String senha) {
        String e = normalizar(email);
        return usuarios.findByEmail(e).orElseGet(() -> {
            Usuario u = novo(e, senha, PapelUsuario.ADMIN);
            u.setEmailVerificado(true);
            u.setEmailVerificadoEm(OffsetDateTime.now());
            u.setTermosVersao("admin");
            return usuarios.save(u);
        });
    }

    // ------------------------------------------------------------------ login e sessão
    /**
     * Valida as credenciais. A verificação de e-mail só é cobrada DEPOIS da senha correta, para que o
     * login não sirva para descobrir quais e-mails existem.
     */
    @Transactional
    public Usuario autenticar(String email, String senha) {
        return autenticar(email, senha, null);
    }

    /** Com segundo fator ativo, o código errado conta como falha de login (bloqueio da conta); a ausência dele, não. */
    @Transactional
    public Usuario autenticar(String email, String senha, String codigoTotp) {
        String e = normalizar(email);
        if (tentativas.bloqueado(e)) {
            throw new TentativasExcedidasException(tentativas.segundosRestantes());
        }
        Usuario u = usuarios.findByEmail(e).orElse(null);
        String hash = u != null ? u.getSenhaHash() : hashFalso;
        boolean ok = encoder.matches(senha, hash);
        if (u == null || !ok) {
            tentativas.registrarFalha(e);
            throw new CredenciaisInvalidasException();
        }
        if (u.isTotpAtivo()) {
            if (codigoTotp == null || codigoTotp.isBlank()) throw new TotpRequeridoException();
            if (!totp.confirmar(u, codigoTotp)) {
                tentativas.registrarFalha(e);
                throw new CredenciaisInvalidasException();
            }
        }
        tentativas.limpar(e);
        if (!u.isEmailVerificado()) throw new EmailNaoVerificadoException();
        return u;
    }

    /** Emite o par access/refresh para um usuário já autenticado. */
    public Tokens iniciarSessao(Usuario u, String userAgent) {
        SessaoService.Aberta s = sessoes.abrir(u.getId(), userAgent);
        auditoria.registrar(u.getId(), u.getPapel().name(), "LOGIN", "usuario", u.getId(), null);
        return montar(u, s.refreshToken(), s.sessaoId());
    }

    /** Troca o refresh token por um par novo (rotação). Reuso de token antigo revoga a família inteira. */
    public Tokens renovar(String refreshToken, String userAgent) {
        SessaoService.Rotacao r = sessoes.rotacionar(refreshToken, userAgent);
        Usuario u = usuarios.findById(r.usuarioId()).orElse(null);
        if (u == null || !u.isEmailVerificado()) {
            sessoes.encerrar(r.novoRefreshToken());
            throw new SessaoInvalidaException();
        }
        return montar(u, r.novoRefreshToken(), r.sessaoId());
    }

    public void sair(String refreshToken) {
        sessoes.encerrar(refreshToken);
    }

    private Tokens montar(Usuario u, String refresh, java.util.UUID sessaoId) {
        return new Tokens(jwt.emitir(u, sessaoId), jwt.getTtl().toSeconds(), refresh, sessoes.getValidade().toSeconds());
    }

    // ------------------------------------------------------------------ utilitários
    private Usuario novo(String email, String senha, PapelUsuario papel) {
        SenhaPolicy.validar(senha, email);
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setSenhaHash(encoder.encode(senha));
        u.setPapel(papel);
        u.setTermosVersao(Termos.VERSAO);
        u.setTermosAceitosEm(OffsetDateTime.now());
        return u;
    }

    private void garantirEmailLivre(String email) {
        if (usuarios.existsByEmail(email)) {
            throw new RegraNegocioException("E-mail já cadastrado.");
        }
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
