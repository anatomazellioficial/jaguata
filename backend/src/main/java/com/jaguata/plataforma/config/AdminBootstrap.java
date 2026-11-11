package com.jaguata.plataforma.config;

import com.jaguata.plataforma.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Cria o primeiro ADMIN a partir de ADMIN_EMAIL / ADMIN_PASSWORD, se informados e ainda inexistente. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final AuthService auth;
    private final String email;
    private final String senha;

    public AdminBootstrap(AuthService auth,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String senha) {
        this.auth = auth;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || senha.isBlank()) return;
        if (senha.length() < 8) {
            throw new IllegalStateException("ADMIN_PASSWORD deve ter no mínimo 8 caracteres.");
        }
        auth.criarAdminSeAusente(email, senha);
    }
}
