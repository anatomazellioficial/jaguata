package com.jaguata.plataforma.email;

/** Envio de e-mails transacionais (texto puro). Implementações: SMTP (padrão) e log (desenvolvimento). */
public interface EmailSender {
    /** Nunca lança exceção para o chamador: falhas de envio são registradas e não vazam para a resposta HTTP. */
    void enviar(String para, String assunto, String corpo);
}
