package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.Empresa;
import com.jaguata.plataforma.domain.Ong;
import com.jaguata.plataforma.domain.Usuario;
import com.jaguata.plataforma.domain.enums.PapelUsuario;
import com.jaguata.plataforma.domain.enums.NivelCapacidade;
import com.jaguata.plataforma.exception.ConsultaNaoEncontradaException;
import com.jaguata.plataforma.exception.EntradaInvalidaException;
import com.jaguata.plataforma.integracao.ConsultaService;
import com.jaguata.plataforma.integracao.DadosCnpj;
import com.jaguata.plataforma.integracao.Documentos;
import com.jaguata.plataforma.integracao.Localizacao;
import com.jaguata.plataforma.exception.CredenciaisInvalidasException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.exception.TentativasExcedidasException;
import com.jaguata.plataforma.repository.UsuarioRepository;
import com.jaguata.plataforma.security.LoginAttemptService;
import com.jaguata.plataforma.security.SenhaPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;

@Service
public class AuthService {

    /** Hash descartável para igualar o tempo de resposta quando o e-mail não existe. */
    private final String hashFalso;

    private final UsuarioRepository usuarios;
    private final EmpresaService empresas;
    private final OngService ongs;
    private final PasswordEncoder encoder;
    private final LoginAttemptService tentativas;
    private final ConsultaService consulta;
    private final TransactionTemplate tx;

    public AuthService(UsuarioRepository usuarios, EmpresaService empresas, OngService ongs,
                       PasswordEncoder encoder, LoginAttemptService tentativas, ConsultaService consulta,
                       TransactionTemplate tx) {
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.ongs = ongs;
        this.encoder = encoder;
        this.tentativas = tentativas;
        this.consulta = consulta;
        this.tx = tx;
        this.hashFalso = encoder.encode("senha-inexistente");
    }

    /**
     * Cadastro de empresa. Razão social, CNAE e porte vêm da Receita Federal e o endereço/coordenadas, do CEP
     * da sede; o cliente informa apenas o CNPJ. As consultas externas rodam ANTES da transação, para não
     * segurar conexão do banco enquanto aguardam a rede.
     */
    public Usuario registrarEmpresa(String email, String senha, String cnpjBruto) {
        String e = normalizar(email);
        SenhaPolicy.validar(senha, e);
        garantirEmailLivre(e);
        String cnpj = Documentos.cnpj(cnpjBruto);
        if (empresas.cnpjCadastrado(cnpj)) throw new RegraNegocioException("Já existe empresa cadastrada com este CNPJ.");

        DadosCnpj dados = consultarAtivo(cnpj);
        Localizacao loc = localizarSede(dados);
        return tx.execute(s -> {
            Empresa empresa = empresas.criar(dados, loc);
            Usuario u = novo(e, senha, PapelUsuario.EMPRESA);
            u.setEmpresaId(empresa.getId());
            return usuarios.save(u);
        });
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
        return tx.execute(s -> {
            Ong ong = ongs.criar(dados, loc, nivel);
            Usuario u = novo(e, senha, PapelUsuario.ONG);
            u.setOngId(ong.getId());
            return usuarios.save(u);
        });
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

    @Transactional
    public Usuario criarAdminSeAusente(String email, String senha) {
        String e = normalizar(email);
        return usuarios.findByEmail(e).orElseGet(() -> usuarios.save(novo(e, senha, PapelUsuario.ADMIN)));
    }

    @Transactional(readOnly = true)
    public Usuario autenticar(String email, String senha) {
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
        tentativas.limpar(e);
        return u;
    }

    private Usuario novo(String email, String senha, PapelUsuario papel) {
        SenhaPolicy.validar(senha, email);
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setSenhaHash(encoder.encode(senha));
        u.setPapel(papel);
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
