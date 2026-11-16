-- V7 - Avisos por e-mail: preferências por tipo e resumo diário
ALTER TABLE usuarios
    ADD COLUMN notif_sugestoes      BOOLEAN NOT NULL DEFAULT TRUE,    -- novas sugestões de candidatos (ONG)
    ADD COLUMN notif_andamento      BOOLEAN NOT NULL DEFAULT TRUE,    -- candidato enviado, entrevista, contratação
    ADD COLUMN notif_resumo_diario  BOOLEAN NOT NULL DEFAULT FALSE;   -- junta tudo em um e-mail por dia

-- Fila dos avisos de quem escolheu o resumo diário (sem nome de candidato: só tipo, vaga e quantidade)
CREATE TABLE notificacoes_pendentes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id  UUID         NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    tipo        VARCHAR(20)  NOT NULL CHECK (tipo IN ('SUGESTOES', 'ENVIADO', 'ENTREVISTA', 'CONTRATACAO')),
    titulo_vaga VARCHAR(120),
    quantidade  INTEGER      NOT NULL DEFAULT 1 CHECK (quantidade > 0),
    criada_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notif_pendentes_usuario ON notificacoes_pendentes (usuario_id, criada_em);
