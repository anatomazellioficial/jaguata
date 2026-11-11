-- =====================================================================
-- V1 - Schema inicial: Plataforma de Empregabilidade (Empresas x ONGs)
-- PostgreSQL 15+ com PostGIS
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------------
-- ENUMS
-- ---------------------------------------------------------------------
-- Porte definido exclusivamente pelo faturamento (Receita). Sem nº de funcionários.
CREATE TYPE porte_empresa     AS ENUM ('MEI', 'ME', 'EPP', 'DEMAIS');
CREATE TYPE nivel_capacidade  AS ENUM ('NIVEL_1', 'NIVEL_2', 'NIVEL_3');
CREATE TYPE modelo_trabalho   AS ENUM ('PRESENCIAL', 'HIBRIDO', 'REMOTO');
CREATE TYPE categoria_tag     AS ENUM ('DEMOGRAFIA', 'VULNERABILIDADE', 'SKILL');
-- Cardápio fechado de cargos (sem texto livre)
CREATE TYPE nivel_cargo       AS ENUM (
    'APRENDIZ', 'ESTAGIARIO', 'TRAINEE', 'ASSISTENTE', 'ANALISTA',
    'COORDENADOR', 'GERENTE', 'DIRETOR', 'CEO'
);
CREATE TYPE tipo_contrato     AS ENUM ('FIXO', 'TEMPORARIO', 'POR_PROJETO', 'FREELANCE');
CREATE TYPE status_vaga       AS ENUM ('ABERTA', 'PAUSADA', 'PREENCHIDA', 'ENCERRADA');
CREATE TYPE status_match      AS ENUM ('SUGERIDO', 'ENVIADO', 'ENTREVISTA', 'CONTRATADO', 'RECUSADO');

-- ---------------------------------------------------------------------
-- EMPRESAS
-- ---------------------------------------------------------------------
CREATE TABLE empresas (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj           VARCHAR(14)  NOT NULL,
    razao_social   VARCHAR(255) NOT NULL,
    cnae_principal VARCHAR(10)  NOT NULL,
    porte_receita  porte_empresa NOT NULL,
    cep            VARCHAR(8)   NOT NULL,
    lat            DOUBLE PRECISION NOT NULL,
    lng            DOUBLE PRECISION NOT NULL,
    localizacao    GEOGRAPHY(Point, 4326)
                   GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography) STORED,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_empresas_cnpj UNIQUE (cnpj),
    CONSTRAINT ck_empresas_cnpj CHECK (cnpj ~ '^[0-9]{14}$'),
    CONSTRAINT ck_empresas_cep  CHECK (cep ~ '^[0-9]{8}$'),
    CONSTRAINT ck_empresas_lat  CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_empresas_lng  CHECK (lng BETWEEN -180 AND 180)
);
CREATE INDEX idx_empresas_cnae ON empresas (cnae_principal);
CREATE INDEX idx_empresas_geo  ON empresas USING GIST (localizacao);

-- ---------------------------------------------------------------------
-- ONGS
-- ---------------------------------------------------------------------
CREATE TABLE ongs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj             VARCHAR(14)  NOT NULL,
    razao_social     VARCHAR(255) NOT NULL,
    cnae_principal   VARCHAR(10)  NOT NULL,
    nivel_capacidade nivel_capacidade NOT NULL DEFAULT 'NIVEL_1',
    cep              VARCHAR(8)   NOT NULL,
    lat              DOUBLE PRECISION NOT NULL,
    lng              DOUBLE PRECISION NOT NULL,
    localizacao      GEOGRAPHY(Point, 4326)
                     GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography) STORED,
    is_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_ongs_cnpj UNIQUE (cnpj),
    CONSTRAINT ck_ongs_cnpj CHECK (cnpj ~ '^[0-9]{14}$'),
    CONSTRAINT ck_ongs_cep  CHECK (cep ~ '^[0-9]{8}$'),
    CONSTRAINT ck_ongs_lat  CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_ongs_lng  CHECK (lng BETWEEN -180 AND 180)
);
CREATE INDEX idx_ongs_cnae ON ongs (cnae_principal);
CREATE INDEX idx_ongs_geo  ON ongs USING GIST (localizacao);

-- ---------------------------------------------------------------------
-- CANDIDATOS  (HARD CONSTRAINT: idade >= 18 anos completos)
-- ---------------------------------------------------------------------
CREATE TABLE candidatos (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ong_id               UUID         NOT NULL REFERENCES ongs (id),
    nome_completo        VARCHAR(255) NOT NULL,
    data_nascimento      DATE         NOT NULL,
    cep                  VARCHAR(8)   NOT NULL,
    lat                  DOUBLE PRECISION NOT NULL,
    lng                  DOUBLE PRECISION NOT NULL,
    localizacao          GEOGRAPHY(Point, 4326)
                         GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography) STORED,
    modelo_trabalho_pref modelo_trabalho NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_candidatos_idade_minima
        CHECK (data_nascimento <= (CURRENT_DATE - INTERVAL '18 years')::date),
    CONSTRAINT ck_candidatos_cep CHECK (cep ~ '^[0-9]{8}$'),
    CONSTRAINT ck_candidatos_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_candidatos_lng CHECK (lng BETWEEN -180 AND 180),
    -- permite FK composta em matches garantindo que a ONG do match é a ONG do candidato
    CONSTRAINT uq_candidatos_id_ong UNIQUE (id, ong_id)
);
CREATE INDEX idx_candidatos_ong ON candidatos (ong_id);
CREATE INDEX idx_candidatos_geo ON candidatos USING GIST (localizacao);

-- Defesa em profundidade: o CHECK acima só é avaliado em INSERT/UPDATE da linha;
-- o trigger garante a mesma regra e uma mensagem de erro clara.
CREATE FUNCTION fn_valida_idade_candidato() RETURNS trigger AS $$
BEGIN
    IF NEW.data_nascimento > (CURRENT_DATE - INTERVAL '18 years')::date THEN
        RAISE EXCEPTION 'Candidato deve ter 18 anos completos (data_nascimento=%)', NEW.data_nascimento
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_candidatos_idade
    BEFORE INSERT OR UPDATE OF data_nascimento ON candidatos
    FOR EACH ROW EXECUTE FUNCTION fn_valida_idade_candidato();

-- ---------------------------------------------------------------------
-- CANDIDATO_TAGS
-- ---------------------------------------------------------------------
CREATE TABLE candidato_tags (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidato_id UUID         NOT NULL REFERENCES candidatos (id) ON DELETE CASCADE,
    categoria    categoria_tag NOT NULL,
    tag          VARCHAR(80)  NOT NULL,   -- ex: 'PCD', 'Egresso', '50+'
    CONSTRAINT uq_candidato_tag UNIQUE (candidato_id, categoria, tag)
);
CREATE INDEX idx_candidato_tags_tag ON candidato_tags (categoria, tag);

-- ---------------------------------------------------------------------
-- VAGAS
-- ---------------------------------------------------------------------
CREATE TABLE vagas (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id       UUID          NOT NULL REFERENCES empresas (id),
    nivel_cargo      nivel_cargo   NOT NULL,          -- escolhido pela empresa (cardápio)
    titulo_exibicao  VARCHAR(80)   NOT NULL,          -- visão da ONG (Matriz de Complexidade)
    tipo_contrato    tipo_contrato NOT NULL,
    modelo_trabalho  modelo_trabalho NOT NULL,
    cep_atuacao      VARCHAR(8)    NOT NULL,
    lat              DOUBLE PRECISION NOT NULL,
    lng              DOUBLE PRECISION NOT NULL,
    localizacao      GEOGRAPHY(Point, 4326)
                     GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography) STORED,
    status           status_vaga   NOT NULL DEFAULT 'ABERTA',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_vagas_cep CHECK (cep_atuacao ~ '^[0-9]{8}$'),
    CONSTRAINT ck_vagas_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_vagas_lng CHECK (lng BETWEEN -180 AND 180)
);
CREATE INDEX idx_vagas_empresa ON vagas (empresa_id);
CREATE INDEX idx_vagas_status  ON vagas (status);
CREATE INDEX idx_vagas_geo     ON vagas USING GIST (localizacao);

-- ---------------------------------------------------------------------
-- MATCHES (funil)
-- ---------------------------------------------------------------------
CREATE TABLE matches (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vaga_id      UUID         NOT NULL REFERENCES vagas (id),
    candidato_id UUID         NOT NULL,
    ong_id       UUID         NOT NULL REFERENCES ongs (id),
    match_score  NUMERIC(5,2) NOT NULL DEFAULT 0,
    status       status_match NOT NULL DEFAULT 'SUGERIDO',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_match_vaga_candidato UNIQUE (vaga_id, candidato_id),
    CONSTRAINT ck_match_score CHECK (match_score BETWEEN 0 AND 100),
    -- a ONG do match tem de ser a ONG dona do candidato
    CONSTRAINT fk_match_candidato_ong FOREIGN KEY (candidato_id, ong_id)
        REFERENCES candidatos (id, ong_id)
);
CREATE INDEX idx_matches_vaga   ON matches (vaga_id);
CREATE INDEX idx_matches_ong    ON matches (ong_id, status);
CREATE INDEX idx_matches_cand   ON matches (candidato_id);

-- Regra: vaga de APRENDIZ só aceita candidatos de 18 a 24 anos.
CREATE FUNCTION fn_valida_aprendiz_match() RETURNS trigger AS $$
DECLARE
    v_nivel nivel_cargo;
    v_nasc  DATE;
BEGIN
    SELECT nivel_cargo INTO v_nivel FROM vagas WHERE id = NEW.vaga_id;
    IF v_nivel = 'APRENDIZ' THEN
        SELECT data_nascimento INTO v_nasc FROM candidatos WHERE id = NEW.candidato_id;
        IF v_nasc <= (CURRENT_DATE - INTERVAL '25 years')::date THEN
            RAISE EXCEPTION 'Vaga de Aprendiz é restrita a candidatos de 18 a 24 anos'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_matches_aprendiz
    BEFORE INSERT ON matches
    FOR EACH ROW EXECUTE FUNCTION fn_valida_aprendiz_match();
