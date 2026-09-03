CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE event (
    id            BIGSERIAL PRIMARY KEY,
    public_id     UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name          VARCHAR(120) NOT NULL,
    description   TEXT,
    venue         VARCHAR(160) NOT NULL,
    gate_opens_at TIMESTAMP   NOT NULL,
    starts_at     TIMESTAMP   NOT NULL,
    ends_at       TIMESTAMP   NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at    TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT event_period_ck CHECK (ends_at > starts_at),
    CONSTRAINT event_gate_ck   CHECK (gate_opens_at <= starts_at)
);

CREATE TABLE ticket_batch (
    id             BIGSERIAL PRIMARY KEY,
    public_id      UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    event_id       BIGINT      NOT NULL REFERENCES event(id),
    name           VARCHAR(60) NOT NULL,
    price_cents    INTEGER     NOT NULL,
    total_quantity INTEGER     NOT NULL,
    sold_quantity  INTEGER     NOT NULL DEFAULT 0,
    sales_start    TIMESTAMP   NOT NULL,
    sales_end      TIMESTAMP   NOT NULL,
    CONSTRAINT batch_price_ck CHECK (price_cents > 0),
    CONSTRAINT batch_total_ck CHECK (total_quantity > 0),
    CONSTRAINT batch_sales_ck CHECK (sales_end > sales_start),
    -- rede de seguranca final contra oversell: o banco recusa, aconteca o que acontecer
    CONSTRAINT batch_sold_ck  CHECK (sold_quantity >= 0 AND sold_quantity <= total_quantity)
);
CREATE INDEX idx_batch_event ON ticket_batch(event_id);

CREATE TABLE buyer (
    id         BIGSERIAL PRIMARY KEY,
    public_id  UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name       VARCHAR(120) NOT NULL,
    email      VARCHAR(160) NOT NULL,
    document   VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT buyer_document_uk UNIQUE (document)
);

CREATE TABLE purchase_order (
    id          BIGSERIAL PRIMARY KEY,
    public_id   UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    buyer_id    BIGINT      NOT NULL REFERENCES buyer(id),
    batch_id    BIGINT      NOT NULL REFERENCES ticket_batch(id),
    quantity    INTEGER     NOT NULL,
    total_cents INTEGER     NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    expires_at  TIMESTAMP   NOT NULL,
    paid_at     TIMESTAMP,
    CONSTRAINT order_quantity_ck CHECK (quantity > 0 AND quantity <= 6)
);
CREATE INDEX idx_order_status_expires ON purchase_order(status, expires_at);

CREATE TABLE ticket (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    order_id        BIGINT       NOT NULL REFERENCES purchase_order(id),
    batch_id        BIGINT       NOT NULL REFERENCES ticket_batch(id),
    holder_name     VARCHAR(120) NOT NULL,
    holder_document VARCHAR(20),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ISSUED',
    checked_in_at   TIMESTAMP,
    checked_in_by   VARCHAR(120),
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ticket_checkin_ck CHECK (
        (status = 'USED' AND checked_in_at IS NOT NULL)
        OR (status <> 'USED' AND checked_in_at IS NULL)
    )
);
CREATE INDEX idx_ticket_order ON ticket(order_id);
CREATE INDEX idx_ticket_batch ON ticket(batch_id);
