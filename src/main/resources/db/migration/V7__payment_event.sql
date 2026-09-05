-- Problema P4 — o gateway reenvia a mesma notificacao.
--
-- Gateway de pagamento reenvia. Nao e falha: e o contrato deles, que garante
-- "pelo menos uma entrega" e nao "exatamente uma". Timeout da sua ponta, deploy
-- no meio do processamento, retry programado — a mesma notificacao chega duas,
-- cinco, vinte vezes. Sem defesa, o pedido e pago varias vezes e os ingressos
-- sao emitidos varias vezes.
--
-- A defesa e a mesma dos P1 e P3: uma escrita condicional, e a decisao vem do
-- numero de linhas afetadas. Aqui a condicao e o UNIQUE.

CREATE TABLE payment_event (
    id           BIGSERIAL PRIMARY KEY,
    -- o identificador da notificacao NO GATEWAY. E ele que se repete, e e ele
    -- que o UNIQUE bloqueia
    external_id  VARCHAR(120) NOT NULL,
    order_id     BIGINT       REFERENCES purchase_order(id),
    status       VARCHAR(20)  NOT NULL,
    received_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT payment_event_external_uk UNIQUE (external_id)
);
CREATE INDEX idx_payment_event_order ON payment_event (order_id);

-- Referencia que o gateway conhece por fora. No Mercado Pago seria o
-- external_reference enviado na criacao da preferencia; a notificacao traz o id
-- do pagamento, e a consulta a API devolve esta referencia de volta.
--
-- DEFAULT gen_random_uuid() para os pedidos que ja existem receberem valor;
-- a aplicacao gera o seu proprio na criacao.
ALTER TABLE purchase_order
    ADD COLUMN payment_reference UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE purchase_order
    ADD CONSTRAINT purchase_order_payment_ref_uk UNIQUE (payment_reference);
