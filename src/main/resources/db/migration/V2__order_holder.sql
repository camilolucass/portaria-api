-- Os titulares chegam na criacao do pedido, mas a RN-07 so permite gerar os
-- ingressos na transicao para PAID. Esta tabela guarda os titulares nesse
-- intervalo; no pagamento, cada linha vira exatamente um ticket.
CREATE TABLE order_holder (
    id           BIGSERIAL PRIMARY KEY,
    public_id    UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    order_id     BIGINT       NOT NULL REFERENCES purchase_order(id),
    holder_index INTEGER      NOT NULL,
    name         VARCHAR(120) NOT NULL,
    document     VARCHAR(20),
    CONSTRAINT order_holder_position_uk    UNIQUE (order_id, holder_index),
    CONSTRAINT order_holder_position_ck    CHECK (holder_index >= 0)
);
CREATE INDEX idx_order_holder_order ON order_holder(order_id);
