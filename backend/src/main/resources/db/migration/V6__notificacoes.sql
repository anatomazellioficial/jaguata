-- V6 - Preferência de notificações por e-mail (nova sugestão, candidato enviado, entrevista, contratação)
ALTER TABLE usuarios
    ADD COLUMN notificacoes_email BOOLEAN NOT NULL DEFAULT TRUE;
