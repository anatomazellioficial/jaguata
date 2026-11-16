package com.jaguata.plataforma;

import com.jaguata.plataforma.email.EmailSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dublê de e-mail para os testes: guarda o que seria enviado e extrai os tokens dos links. */
public class CapturaEmails implements EmailSender {

    public record Email(String para, String assunto, String corpo) {
    }

    private final List<Email> enviados = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(String para, String assunto, String corpo) {
        enviados.add(new Email(para.toLowerCase(), assunto, corpo));
    }

    public List<Email> para(String email) {
        return enviados.stream().filter(e -> e.para().equals(email.toLowerCase())).toList();
    }

    /** Token do último link {@code /<caminho>?token=...} enviado a esse e-mail; null se não houver. */
    public String ultimoToken(String email, String caminho) {
        List<Email> lista = para(email);
        Pattern p = Pattern.compile("/" + Pattern.quote(caminho) + "\\?token=([A-Za-z0-9_-]+)");
        for (int i = lista.size() - 1; i >= 0; i--) {
            Matcher m = p.matcher(lista.get(i).corpo());
            if (m.find()) return m.group(1);
        }
        return null;
    }

    public long quantos(String email, String trechoDoAssunto) {
        return para(email).stream().filter(e -> e.assunto().contains(trechoDoAssunto)).count();
    }
}
