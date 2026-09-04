-- Indices para GET /events/{publicId}/stats.
--
-- As duas consultas de estatistica agrupam por lote e filtram por status:
--   ticket        -> quantos emitidos e quantos ja entraram, por lote
--   purchase_order-> receita, somando apenas os pedidos PAID
--
-- Sem estes indices o Postgres varre a tabela inteira de ingressos a cada
-- abertura do painel. Com 200 mil ingressos isso e um seq scan por request.

CREATE INDEX idx_ticket_batch_status ON ticket (batch_id, status);
CREATE INDEX idx_order_batch_status  ON purchase_order (batch_id, status);

-- idx_ticket_batch (batch_id) da V1 vira redundante: e prefixo exato do indice
-- composto acima, entao o planner nunca mais o escolheria. Manter os dois so
-- custa escrita em todo INSERT de ingresso e espaco em disco.
DROP INDEX idx_ticket_batch;
