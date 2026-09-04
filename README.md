# portaria-api

API REST para venda de ingressos e controle de entrada por QR Code em eventos.

Resolve quatro problemas que um CRUD nao resolve: oversell sob concorrencia,
QR Code falsificado, entrada duplicada do mesmo ingresso e notificacao repetida
do gateway de pagamento. A especificacao completa esta em [SPEC.md](SPEC.md).

## Stack

Java 25 · Spring Boot 4.1.1 · PostgreSQL 16 · Flyway · Spring Data JPA
(`ddl-auto=validate`) · springdoc-openapi · ZXing · JUnit 5 + Testcontainers

> O SPEC fixa Java 21 + Spring Boot 3.3.x. Este repositorio roda em Java 25 com
> Boot 4.1.1 por decisao de ambiente — ver `CLAUDE.md`.

## Rodando

```bash
docker compose up -d          # Postgres 16 em localhost:5432
./mvnw verify                 # build + testes (precisa do Docker para os Testcontainers)
./mvnw spring-boot:run
curl http://localhost:8080/actuator/health
```

Variaveis: `DB_URL`, `DB_USER`, `DB_PASSWORD` tem valor padrao para o
docker-compose local. `QR_SECRET` (minimo 32 caracteres) passa a ser
obrigatorio a partir da Etapa 4.

## Estado

Fase 1, **Etapa 3** concluida.

- Etapa 1 — schema, entidades, enums e repositories.
- Etapa 2 — CRUD de eventos e lotes, publicacao, Bean Validation e
  `GlobalExceptionHandler` com `ProblemDetail` (RFC 7807).
- Etapa 3 — pedidos com reserva atomica de estoque (P1), expiracao automatica
  e emissao de ingressos no pagamento.

Sem QR nem check-in ainda — ver secao 11 do SPEC.

### Como o oversell e evitado (P1)

A soma acontece dentro do proprio `UPDATE`, e a decisao vem do numero de linhas
afetadas:

```sql
UPDATE ticket_batch
   SET sold_quantity = sold_quantity + :quantity
 WHERE id = :batchId
   AND sold_quantity + :quantity <= total_quantity
```

Zero linhas afetadas significa lote esgotado — `409`. Sem lock explicito, sem
`@Version`, sem retry. O `CHECK (sold_quantity <= total_quantity)` continua no
banco como rede final: removendo a condicao do `UPDATE`, o `OrderConcurrencyTest`
falha e o `CHECK` dispara.

## Rotas disponiveis

| Metodo | Rota | |
|---|---|---|
| POST | `/api/v1/events` | cria em DRAFT — 201 |
| POST | `/api/v1/events/{publicId}/publish` | DRAFT -> PUBLISHED — 200 / 409 |
| GET | `/api/v1/events` | paginado |
| GET | `/api/v1/events/{publicId}` | 200 / 404 |
| POST | `/api/v1/events/{eventPublicId}/batches` | 201 / 422 |
| GET | `/api/v1/events/{eventPublicId}/batches` | 200, com `availableQuantity` |
| POST | `/api/v1/orders` | reserva estoque — 201 / 409 / 422 / 400 |
| POST | `/api/v1/orders/{publicId}/pay` | emite os ingressos — 200 / 409 |
| POST | `/api/v1/orders/{publicId}/cancel` | RN-08 — 200 / 409 |
| GET | `/api/v1/orders/{publicId}` | 200 com os ingressos / 404 |
