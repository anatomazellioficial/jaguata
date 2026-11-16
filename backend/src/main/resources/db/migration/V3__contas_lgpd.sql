-- V3 - Contas (verificação de e-mail, recuperação de senha, sessões) e LGPD (consentimento, auditoria)

-- ---------------------------------------------------------------------
-- Usuários: e-mail verificado, aceite dos Termos de Uso e troca de senha
-- ---------------------------------------------------------------------
ALTER TABLE usuarios
    ADD COLUMN email_verificado    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN email_verificado_em TIMESTAMPTZ,
    ADD COLUMN termos_versao       VARCHAR(20),
    ADD COLUMN termos_aceitos_em   TIMESTAMPTZ,
    ADD COLUMN senha_alterada_em   TIMESTAMPTZ;

-- contas anteriores a esta migração são consideradas verificadas (nada a reenviar)
UPDATE usuarios
   SET email_verificado = TRUE, email_verificado_em = created_at,
       termos_versao = 'legado', termos_aceitos_em = created_at;

-- ---------------------------------------------------------------------
-- Tokens de uso único enviados por e-mail (guardamos só o hash SHA-256)
-- ---------------------------------------------------------------------
CREATE TYPE tipo_token_conta AS ENUM ('VERIFICACAO_EMAIL', 'REDEFINICAO_SENHA');

CREATE TABLE tokens_conta (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID             NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    tipo       tipo_token_conta NOT NULL,
    token_hash VARCHAR(64)      NOT NULL,
    criado_em  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    expira_em  TIMESTAMPTZ      NOT NULL,
    usado_em   TIMESTAMPTZ,
    CONSTRAINT uq_tokens_conta_hash UNIQUE (token_hash)
);
CREATE INDEX idx_tokens_conta_usuario ON tokens_conta (usuario_id, tipo);
CREATE INDEX idx_tokens_conta_expira  ON tokens_conta (expira_em);

-- ---------------------------------------------------------------------
-- Sessões (refresh tokens rotativos, agrupados por "família" para detectar reuso)
-- ---------------------------------------------------------------------
CREATE TABLE sessoes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL,
    familia         UUID        NOT NULL,
    criada_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_em       TIMESTAMPTZ NOT NULL,
    revogada_em     TIMESTAMPTZ,
    substituida_por UUID,
    user_agent      VARCHAR(200),
    CONSTRAINT uq_sessoes_hash UNIQUE (token_hash)
);
CREATE INDEX idx_sessoes_usuario ON sessoes (usuario_id);
CREATE INDEX idx_sessoes_familia ON sessoes (familia);
CREATE INDEX idx_sessoes_expira  ON sessoes (expira_em);

-- ---------------------------------------------------------------------
-- Candidatos: consentimento atestado pela ONG e anonimização (direitos do titular)
-- ---------------------------------------------------------------------
ALTER TABLE candidatos
    ADD COLUMN consentimento_em         TIMESTAMPTZ,
    ADD COLUMN consentimento_versao     VARCHAR(20),
    ADD COLUMN consentimento_usuario_id UUID,     -- quem atestou (sem FK: sobrevive à exclusão da conta)
    ADD COLUMN anonimizado_em           TIMESTAMPTZ;

UPDATE candidatos
   SET consentimento_em = created_at, consentimento_versao = 'legado';

CREATE INDEX idx_candidatos_ativos ON candidatos (ong_id) WHERE anonimizado_em IS NULL;

-- ---------------------------------------------------------------------
-- Integridade: um candidato só pode ser CONTRATADO uma vez (vale mesmo numa corrida)
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX uq_match_candidato_contratado ON matches (candidato_id) WHERE status = 'CONTRATADO';

-- ---------------------------------------------------------------------
-- Auditoria: quem fez o quê (sem dados pessoais no detalhe; sem FK para sobreviver às exclusões)
-- ---------------------------------------------------------------------
CREATE TABLE auditoria (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quando      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    usuario_id  UUID,
    papel       VARCHAR(20),
    acao        VARCHAR(60)  NOT NULL,
    entidade    VARCHAR(40),
    entidade_id UUID,
    detalhe     VARCHAR(300)
);
CREATE INDEX idx_auditoria_quando  ON auditoria (quando DESC);
CREATE INDEX idx_auditoria_acao    ON auditoria (acao, quando DESC);
CREATE INDEX idx_auditoria_usuario ON auditoria (usuario_id, quando DESC);
