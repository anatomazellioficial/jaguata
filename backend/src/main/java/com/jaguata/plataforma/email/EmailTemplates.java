package com.jaguata.plataforma.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Textos dos e-mails transacionais. Os links apontam para o frontend ({@code app.base-url}). */
@Component
public class EmailTemplates {

    private final String base;

    public EmailTemplates(@Value("${app.base-url:http://localhost:3000}") String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public record Mensagem(String assunto, String corpo) {
    }

    public Mensagem verificacaoEmail(String token, long horas) {
        return new Mensagem("Confirme seu e-mail na Jaguatá", """
                Olá!

                Para ativar a sua conta na Jaguatá, confirme este e-mail:

                %s/verificar-email?token=%s

                O link vale por %d horas e só pode ser usado uma vez.
                Se você não criou uma conta, ignore esta mensagem.

                Equipe Jaguatá
                """.formatted(base, token, horas));
    }

    public Mensagem redefinicaoSenha(String token, long minutos) {
        return new Mensagem("Redefinição de senha da Jaguatá", """
                Olá!

                Recebemos um pedido para redefinir a senha da sua conta:

                %s/redefinir-senha?token=%s

                O link vale por %d minutos e só pode ser usado uma vez.
                Se não foi você, ignore esta mensagem: a sua senha continua a mesma.

                Equipe Jaguatá
                """.formatted(base, token, minutos));
    }

    public Mensagem senhaAlterada() {
        return new Mensagem("A senha da sua conta Jaguatá foi alterada", """
                Olá!

                A senha da sua conta foi alterada e todas as sessões foram encerradas.
                Se não foi você, redefina a senha imediatamente em %s/esqueci-senha e avise a nossa equipe.

                Equipe Jaguatá
                """.formatted(base));
    }

    public Mensagem contaExcluida() {
        return new Mensagem("Sua conta Jaguatá foi excluída", """
                Olá!

                Confirmamos a exclusão do seu acesso à Jaguatá. Seus dados de login foram removidos.
                Se não foi você, entre em contato com a nossa equipe.

                Equipe Jaguatá
                """);
    }
}
