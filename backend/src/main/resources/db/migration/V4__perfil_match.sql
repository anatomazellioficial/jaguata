-- V4 - Perfil do candidato, requisitos da vaga, áreas de atuação da ONG e detalhe do score

-- ---------------------------------------------------------------------
-- Tipos
-- ---------------------------------------------------------------------
-- a ordem dos valores importa: o PostgreSQL ordena enums pela ordem de declaração
CREATE TYPE escolaridade AS ENUM (
    'FUNDAMENTAL_INCOMPLETO', 'FUNDAMENTAL', 'MEDIO_INCOMPLETO', 'MEDIO',
    'TECNICO_INCOMPLETO', 'TECNICO', 'SUPERIOR_INCOMPLETO', 'SUPERIOR', 'POS'
);
CREATE TYPE genero AS ENUM ('MULHER', 'HOMEM', 'MULHER_TRANS', 'HOMEM_TRANS', 'NAO_BINARIO', 'OUTRO');
CREATE TYPE raca_cor AS ENUM ('BRANCA', 'PRETA', 'PARDA', 'AMARELA', 'INDIGENA');
CREATE TYPE regime_contratacao AS ENUM ('CLT', 'PJ', 'COOPERADO');

-- ---------------------------------------------------------------------
-- Candidato: formação, regimes aceitos e autodeclarações (opcionais)
-- ---------------------------------------------------------------------
ALTER TABLE candidatos
    ADD COLUMN escolaridade escolaridade,
    ADD COLUMN genero       genero,       -- autodeclaração opcional; nunca exibida às empresas
    ADD COLUMN raca_cor     raca_cor;     -- autodeclaração opcional; nunca exibida às empresas

CREATE TABLE candidato_regimes (
    candidato_id UUID        NOT NULL REFERENCES candidatos (id) ON DELETE CASCADE,
    regime       VARCHAR(20) NOT NULL,
    PRIMARY KEY (candidato_id, regime),
    CONSTRAINT ck_candidato_regime CHECK (regime IN ('CLT', 'PJ', 'COOPERADO'))
);

-- ---------------------------------------------------------------------
-- Vaga: requisitos e ação afirmativa
-- ---------------------------------------------------------------------
ALTER TABLE vagas
    ADD COLUMN escolaridade_minima      escolaridade,
    ADD COLUMN regime                   regime_contratacao,
    ADD COLUMN justificativa_afirmativa VARCHAR(500);

CREATE TABLE vaga_skills (
    vaga_id UUID        NOT NULL REFERENCES vagas (id) ON DELETE CASCADE,
    skill   VARCHAR(80) NOT NULL,
    PRIMARY KEY (vaga_id, skill)
);

CREATE TABLE vaga_grupos (
    vaga_id UUID        NOT NULL REFERENCES vagas (id) ON DELETE CASCADE,
    grupo   VARCHAR(40) NOT NULL,
    PRIMARY KEY (vaga_id, grupo),
    CONSTRAINT ck_vaga_grupo CHECK (grupo IN ('PCD', 'EGRESSO', 'MAIS_50', 'MULHERES', 'PESSOAS_NEGRAS',
                                             'INDIGENAS', 'PESSOAS_TRANS_E_NAO_BINARIAS'))
);

-- vaga afirmativa exige justificativa registrada
ALTER TABLE vagas ADD CONSTRAINT ck_vaga_afirmativa_justificada CHECK (
    justificativa_afirmativa IS NULL OR length(btrim(justificativa_afirmativa)) >= 20
);

-- ---------------------------------------------------------------------
-- ONG: setores de atuação (cruzados com o CNAE da empresa no match)
-- ---------------------------------------------------------------------
CREATE TABLE ong_setores (
    ong_id UUID        NOT NULL REFERENCES ongs (id) ON DELETE CASCADE,
    setor  VARCHAR(40) NOT NULL,
    PRIMARY KEY (ong_id, setor),
    CONSTRAINT ck_ong_setor CHECK (setor IN (
        'AGRONEGOCIO', 'INDUSTRIA', 'UTILIDADES_E_MEIO_AMBIENTE', 'CONSTRUCAO', 'COMERCIO', 'LOGISTICA_E_TRANSPORTE',
        'ALIMENTACAO_E_HOSPEDAGEM', 'TECNOLOGIA_E_COMUNICACAO', 'FINANCEIRO_E_SEGUROS', 'SERVICOS_PROFISSIONAIS',
        'SERVICOS_ADMINISTRATIVOS', 'EDUCACAO', 'SAUDE_E_ASSISTENCIA', 'ARTES_CULTURA_E_ESPORTE', 'OUTROS_SERVICOS'))
);

-- ---------------------------------------------------------------------
-- Match: por que o candidato pontuou assim (JSON com os critérios e pontos; sem dados sensíveis)
-- ---------------------------------------------------------------------
ALTER TABLE matches ADD COLUMN score_detalhe TEXT;

-- ---------------------------------------------------------------------
-- Regra: vaga de ESTAGIÁRIO só aceita quem cursa ensino técnico ou superior
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_valida_estagiario_match() RETURNS trigger AS $$
DECLARE
    v_nivel nivel_cargo;
    v_esc   escolaridade;
BEGIN
    SELECT nivel_cargo INTO v_nivel FROM vagas WHERE id = NEW.vaga_id;
    IF v_nivel = 'ESTAGIARIO' THEN
        SELECT escolaridade INTO v_esc FROM candidatos WHERE id = NEW.candidato_id;
        IF v_esc IS NULL OR v_esc NOT IN ('TECNICO_INCOMPLETO', 'SUPERIOR_INCOMPLETO') THEN
            RAISE EXCEPTION 'Vaga de Estagiário exige ensino técnico ou superior em andamento'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_matches_estagiario
    BEFORE INSERT ON matches
    FOR EACH ROW EXECUTE FUNCTION fn_valida_estagiario_match();
