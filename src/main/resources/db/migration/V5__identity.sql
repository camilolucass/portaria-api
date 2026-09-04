-- Fase 2, Etapa 1 — identidade e autenticacao.
--
-- Papeis ficam em tabela propria, e nao em coluna: um mesmo usuario pode ser
-- organizador de um evento e operador de portaria em outro, e a Fase 2 preve
-- exatamente isso.

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    public_id     UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    -- 255 e folga proposital: BCrypt ocupa 68 com o prefixo {bcrypt}, mas trocar
    -- para Argon2 depois passa de 110, e migrar coluna de senha em producao e
    -- pior do que reservar espaco agora
    password_hash VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT app_user_email_uk UNIQUE (email),
    -- o banco garante o que o codigo promete: e-mail sempre normalizado.
    -- Sem isso, "Ana@x.com" e "ana@x.com" viram duas contas e o UNIQUE nao ajuda
    CONSTRAINT app_user_email_lower_ck CHECK (email = lower(email))
);

CREATE TABLE user_role (
    user_id BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL,
    CONSTRAINT user_role_pk PRIMARY KEY (user_id, role),
    CONSTRAINT user_role_ck CHECK (role IN ('ORGANIZER', 'GATE', 'BUYER'))
);
