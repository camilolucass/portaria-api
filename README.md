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

Fase 1, **Etapa 1** concluida: schema, entidades, enums e repositories.
Sem controllers, services, QR ou check-in ainda — ver secao 11 do SPEC.
