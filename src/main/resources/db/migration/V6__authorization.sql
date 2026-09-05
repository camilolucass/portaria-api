-- Fase 2, Etapa 2 — autorizacao.
--
-- As tres colunas novas nascem NOT NULL de proposito. Evento sem dono, pedido
-- sem comprador ou vinculo de portaria sem usuario sao estados que a Fase 2
-- torna sem sentido, e uma coluna anulavel "so por causa dos dados antigos"
-- vira permanente. Em um banco com linhas anteriores esta migration falha, o
-- que e o comportamento desejado: quem migra decide para quem vao os eventos
-- existentes, em vez de o Flyway inventar um dono.

ALTER TABLE event ADD COLUMN organizer_id BIGINT NOT NULL REFERENCES app_user(id);
CREATE INDEX idx_event_organizer ON event (organizer_id);

-- Operador de portaria so faz check-in dos eventos aos quais esta vinculado.
CREATE TABLE event_staff (
    event_id BIGINT NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    user_id  BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT event_staff_pk PRIMARY KEY (event_id, user_id)
);
-- a PK ja cobre event_id; este indice serve a pergunta inversa,
-- "de quais eventos este operador participa"
CREATE INDEX idx_event_staff_user ON event_staff (user_id);

-- O pedido passa a pertencer a uma conta. O buyer continua existindo: sao
-- coisas diferentes — a conta e quem comprou, o buyer e o dado fiscal da compra,
-- que pode legitimamente ser de outra pessoa.
ALTER TABLE purchase_order ADD COLUMN user_id BIGINT NOT NULL REFERENCES app_user(id);
CREATE INDEX idx_order_user ON purchase_order (user_id);

-- checked_in_by era texto livre vindo do cliente, ou seja, auditoria que o
-- proprio auditado escrevia. Passa a ser o usuario autenticado.
ALTER TABLE ticket DROP COLUMN checked_in_by;
ALTER TABLE ticket ADD COLUMN checked_in_by_user_id BIGINT REFERENCES app_user(id);
