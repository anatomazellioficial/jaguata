-- V5 - Segundo fator (TOTP, RFC 6238) para administradores
ALTER TABLE usuarios
    ADD COLUMN totp_segredo      VARCHAR(255),          -- AES-GCM (iv || texto cifrado), base64; nunca em claro
    ADD COLUMN totp_ativo        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN totp_ultimo_passo BIGINT;                -- janela de 30 s já usada: impede reaproveitar um código

CREATE INDEX idx_sessoes_familia_ativa ON sessoes (familia) WHERE revogada_em IS NULL;
