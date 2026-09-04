-- Seed de demonstracao: 1 evento publicado e 2 lotes, para o repositorio subir
-- com algo navegavel no Swagger sem precisar de setup manual.
--
-- ATENCAO: esta migration roda em qualquer banco, inclusive producao. Se este
-- projeto for para producao de verdade, mova-a para uma location separada
-- (spring.flyway.locations com perfil dev) antes do primeiro deploy.
--
-- As datas sao relativas a data da migration, para o evento nao nascer vencido.
INSERT INTO event (public_id, name, description, venue, gate_opens_at, starts_at, ends_at, status)
VALUES (
    '11111111-1111-4111-8111-111111111111',
    'Festa Universitaria 2026',
    'Open bar ate as 2h. Demonstracao gerada pela migration V3.',
    'Centro de Eventos, Orleans/SC',
    now() + interval '30 days',
    now() + interval '30 days' + interval '1 hour',
    now() + interval '30 days' + interval '7 hours',
    'PUBLISHED'
);

INSERT INTO ticket_batch (public_id, event_id, name, price_cents, total_quantity, sold_quantity, sales_start, sales_end)
VALUES
    ('22222222-2222-4222-8222-222222222222',
     (SELECT id FROM event WHERE public_id = '11111111-1111-4111-8111-111111111111'),
     '1o lote', 4500, 200, 0, now() - interval '1 day', now() + interval '20 days'),
    ('33333333-3333-4333-8333-333333333333',
     (SELECT id FROM event WHERE public_id = '11111111-1111-4111-8111-111111111111'),
     '2o lote', 6000, 300, 0, now() + interval '20 days', now() + interval '29 days');
