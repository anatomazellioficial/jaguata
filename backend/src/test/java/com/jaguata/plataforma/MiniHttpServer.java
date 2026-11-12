package com.jaguata.plataforma;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Servidor HTTP/1.1 mínimo para testes, com sockets bloqueantes (sem selector NIO, que não funciona em
 * alguns ambientes Windows). Suficiente para GET com resposta fixa, atraso e cabeçalhos.
 */
final class MiniHttpServer implements AutoCloseable {

    record Requisicao(String caminho, String query, Map<String, String> cabecalhos) {
    }

    record Resposta(int status, String corpo, long atrasoMs) {
        Resposta(int status, String corpo) {
            this(status, corpo, 0);
        }
    }

    private final ServerSocket socket;
    private final Function<Requisicao, Resposta> handler;
    private volatile boolean ativo = true;

    MiniHttpServer(Function<Requisicao, Resposta> handler) throws IOException {
        this.handler = handler;
        this.socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(this::aceitar, "mini-http-accept");
        t.setDaemon(true);
        t.start();
    }

    int porta() {
        return socket.getLocalPort();
    }

    String url() {
        return "http://127.0.0.1:" + porta();
    }

    private void aceitar() {
        while (ativo) {
            try {
                Socket c = socket.accept();
                Thread t = new Thread(() -> atender(c), "mini-http-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void atender(Socket c) {
        try (c) {
            BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.ISO_8859_1));
            String linha = in.readLine();
            if (linha == null) return;
            String alvo = linha.split(" ")[1];
            Map<String, String> cab = new HashMap<>();
            String l;
            while ((l = in.readLine()) != null && !l.isEmpty()) {
                int i = l.indexOf(':');
                if (i > 0) cab.put(l.substring(0, i).trim().toLowerCase(), l.substring(i + 1).trim());
            }
            int q = alvo.indexOf('?');
            Resposta r = handler.apply(new Requisicao(q < 0 ? alvo : alvo.substring(0, q), q < 0 ? "" : alvo.substring(q + 1), cab));
            if (r.atrasoMs() > 0) Thread.sleep(r.atrasoMs());
            byte[] b = r.corpo().getBytes(StandardCharsets.UTF_8);
            OutputStream out = c.getOutputStream();
            out.write(("HTTP/1.1 " + r.status() + " X\r\nContent-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.write(b);
            out.flush();
        } catch (IOException | InterruptedException ignored) {
            // cliente desistiu (ex.: timeout de leitura): normal nos testes de lentidão
        }
    }

    @Override
    public void close() {
        ativo = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
