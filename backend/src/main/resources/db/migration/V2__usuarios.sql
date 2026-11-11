-- V2 - Usuários e papéis (autenticação)
CREATE TYPE papel_usuario AS ENUM ('EMPRESA', 'ONG', 'ADMIN');

CREATE TABLE usuarios (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(254)  NOT NULL,             -- sempre armazenado em minúsculas
    senha_hash VARCHAR(100)  NOT NULL,             -- BCrypt
    papel      papel_usuario NOT NULL,
    empresa_id UUID REFERENCES empresas (id),
    ong_id     UUID REFERENCES ongs (id),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_email_minusculo CHECK (email = lower(email)),
    -- cada papel tem exatamente o vínculo correspondente
    CONSTRAINT ck_usuarios_vinculo CHECK (
        (papel = 'EMPRESA' AND empresa_id IS NOT NULL AND ong_id IS NULL) OR
        (papel = 'ONG'     AND ong_id IS NOT NULL     AND empresa_id IS NULL) OR
        (papel = 'ADMIN'   AND empresa_id IS NULL     AND ong_id IS NULL)
    )
);
CREATE INDEX idx_usuarios_empresa ON usuarios (empresa_id);
CREATE INDEX idx_usuarios_ong     ON usuarios (ong_id);
