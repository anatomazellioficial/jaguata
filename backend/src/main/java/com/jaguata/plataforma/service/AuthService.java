package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.Empresa;
import com.jaguata.plataforma.domain.Ong;
import com.jaguata.plataforma.domain.Usuario;
import com.jaguata.plataforma.domain.enums.PapelUsuario;
import com.jaguata.plataforma.dto.Requests.EmpresaRequest;
import com.jaguata.plataforma.dto.Requests.OngRequest;
import com.jaguata.plataforma.exception.CredenciaisInvalidasException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.exception.TentativasExcedidasException;
import com.jaguata.plataforma.repository.UsuarioRepository;
import com.jaguata.plataforma.security.LoginAttemptService;
import com.jaguata.plataforma.security.SenhaPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public AuthService(UsuarioRepository usuarios, EmpresaService empresas, OngService ongs,
                       PasswordEncoder encoder, LoginAttemptService tentativas) {
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.ongs = ongs;
        this.encoder = encoder;
        this.tentativas = tentativas;
        this.hashFalso = encoder.encode("senha-inexistente");
    }

    /** Cria a empresa e seu usuário na mesma transação. */
    @Transactional
    public Usuario registrarEmpresa(String email, String senha, EmpresaRequest dados) {
        String e = normalizar(email);
        garantirEmailLivre(e);
        Empresa empresa = empresas.criar(dados);
        Usuario u = novo(e, senha, PapelUsuario.EMPRESA);
        u.setEmpresaId(empresa.getId());
        return usuarios.save(u);
    }

    /** A ONG nasce não verificada; só participa de matches após verificação por um ADMIN. */
    @Transactional
    public Usuario registrarOng(String email, String senha, OngRequest dados) {
        String e = normalizar(email);
        garantirEmailLivre(e);
        Ong ong = ongs.criar(dados);
        Usuario u = novo(e, senha, PapelUsuario.ONG);
        u.setOngId(ong.getId());
        return usuarios.save(u);
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
