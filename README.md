# portaria-api

[![CI](https://github.com/camilolucass/portaria-api/actions/workflows/ci.yml/badge.svg)](https://github.com/camilolucass/portaria-api/actions/workflows/ci.yml)

API REST para venda de ingressos e controle de entrada por QR Code em eventos.

O interesse do projeto nao esta no CRUD. Esta em quatro problemas que um CRUD
nao resolve:

| | Problema | Solucao | Onde |
|---|---|---|---|
| **P1** | Dois compradores chegam juntos e o lote vende mais do que tem | `UPDATE` condicional atomico + `CHECK` no banco | [`TicketBatchRepository`](src/main/java/br/com/portaria/batch/TicketBatchRepository.java) |
| **P2** | Alguem gera um QR Code falso | HMAC-SHA256 com comparacao em tempo constante | [`QrCodeSigner`](src/main/java/br/com/portaria/ticket/QrCodeSigner.java) |
| **P3** | O mesmo ingresso entra duas vezes, em duas portarias ao mesmo tempo | `UPDATE ... WHERE status = 'ISSUED'` conferindo linhas afetadas | [`CheckinService`](src/main/java/br/com/portaria/checkin/CheckinService.java) |
| **P4** | O gateway reenvia a mesma notificacao | tabela de eventos processados com `UNIQUE` | Fase 3 |

A Fase 1, completa aqui, entrega P1, P2 e P3. A especificacao esta em
[SPEC.md](SPEC.md); as regras de trabalho no repositorio, em
[CLAUDE.md](CLAUDE.md).

## Stack

Java 25 · Spring Boot 4.1.1 · PostgreSQL 16 · Flyway · Spring Data JPA com
`ddl-auto=validate` · springdoc-openapi · ZXing · JUnit 5 + Testcontainers

> O SPEC fixa Java 21 + Spring Boot 3.3.x. Este repositorio roda em Java 25 com
> Boot 4.1.1 por decisao de ambiente. O desvio, e as pegadinhas que ele traz,
> estao registrados no [CLAUDE.md](CLAUDE.md).

## Rodando

```bash
cp .env.example .env          # e troque o QR_SECRET por um valor aleatorio
docker compose up -d          # Postgres 16 em localhost:5432

export QR_SECRET=$(sed 's/^QR_SECRET=//' .env)
./mvnw spring-boot:run
```

- Swagger UI: http://localhost:8080/docs
- OpenAPI: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

A migration `V3` ja deixa no banco um evento publicado e dois lotes, entao da
para comprar um ingresso pelo Swagger sem nenhum preparo.

### Testes

```bash
./mvnw verify
```

51 testes, todos contra **Postgres real** via Testcontainers — o SPEC proibe H2
inclusive em teste, porque H2 nao reproduz o comportamento concorrente que os
casos criticos exercitam. Precisa do Docker rodando.

### Container

```bash
docker build -t portaria-api .
docker run --rm --network portaria-api_default -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://db:5432/portaria \
  -e QR_SECRET="$QR_SECRET" portaria-api
```

Imagem multi-stage, ~132 MB, rodando como usuario nao-root com
`TZ=America/Sao_Paulo`.

## Rotas

| Metodo | Rota | |
|---|---|---|
| POST | `/api/v1/events` | cria em DRAFT — 201 |
| POST | `/api/v1/events/{publicId}/publish` | DRAFT para PUBLISHED — 200 / 409 |
| GET | `/api/v1/events` | paginado |
| GET | `/api/v1/events/{publicId}` | 200 / 404 |
| POST | `/api/v1/events/{eventPublicId}/batches` | 201 / 422 |
| GET | `/api/v1/events/{eventPublicId}/batches` | 200, com `availableQuantity` |
| POST | `/api/v1/orders` | reserva estoque — 201 / 409 / 422 / 400 |
| POST | `/api/v1/orders/{publicId}/pay` | emite os ingressos — 200 / 409 |
| POST | `/api/v1/orders/{publicId}/cancel` | RN-08 — 200 / 409 |
| GET | `/api/v1/orders/{publicId}` | 200 com os ingressos / 404 |
| GET | `/api/v1/tickets/{publicId}` | 200 com o `code` assinado / 404 |
| GET | `/api/v1/tickets/{publicId}/qr` | PNG 300x300 |
| POST | `/api/v1/checkins` | 200 GRANTED / 409 / 422 |

Erros seguem RFC 7807 (`application/problem+json`), com `title` em portugues.

## As tres decisoes que sustentam o projeto

### P1 — reserva sem oversell

Ler o saldo e depois somar e o erro classico: entre a leitura e a escrita cabe
outra transacao. A soma acontece **dentro** do `UPDATE`, e quem decide e o
numero de linhas afetadas.

```sql
UPDATE ticket_batch
   SET sold_quantity = sold_quantity + :quantity
 WHERE id = :batchId
   AND sold_quantity + :quantity <= total_quantity
```

Zero linhas significa lote esgotado — `409`. Sem lock explicito, sem `@Version`,
sem retry: o banco ja serializa as escritas concorrentes na mesma linha. O
`CHECK (sold_quantity <= total_quantity)` fica como rede final.

### P2 — QR assinado

O conteudo do QR e `{ticket.public_id}.{HMAC-SHA256(public_id)}`, em Base64
URL-safe sem padding. A verificacao usa `MessageDigest.isEqual`, comparacao em
tempo constante. Formato errado, assinatura errada, UUID malformado e ingresso
inexistente devolvem **a mesma** mensagem e o mesmo `422`: quem ataca nao
descobre qual dos quatro errou.

O segredo vem de `QR_SECRET` e nunca e versionado. Troca-lo invalida todos os QR
ja emitidos.

### P3 — check-in atomico

Mesmo principio do estoque, a condicao vai dentro do `UPDATE`:

```sql
UPDATE ticket
   SET status = 'USED', checked_in_at = :now, checked_in_by = :operator
 WHERE id = :id AND status = 'ISSUED'
```

Entre N portarias lendo o mesmo QR ao mesmo tempo, exatamente uma recebe uma
linha afetada. Nao e meta de desempenho, e requisito funcional (RN-13).

## Os testes que provam isso

Os criterios criticos foram verificados por mutacao — removi a protecao e
conferi que o teste realmente falha:

| Mutacao | Resultado |
|---|---|
| tirar `AND sold_quantity + :quantity <= total_quantity` | `OrderConcurrencyTest` falha; o `CHECK` do banco dispara em 10 threads |
| tirar `AND status = 'ISSUED'` | `CheckinConcurrencyTest` falha: **20 de 20** entram com o mesmo ingresso |
| tirar `clearAutomatically = true` | 9 das 19 recusas saem sem informar o horario da entrada anterior |

A ultima mutacao passou despercebida na primeira tentativa: a linha estava
escrita, como o SPEC pede, mas nenhum teste dependia dela. O teste de
concorrencia passou a exigir que toda recusa informe o horario, e so entao a
mutacao quebrou.

## Estado

Fase 1 completa — as cinco etapas da secao 11 do SPEC, um commit por etapa.
Os dez criterios de aceite (TC-01 a TC-10) estao cobertos.

Proximas fases, ainda nao implementadas: Spring Security + JWT (Fase 2);
Mercado Pago com webhook idempotente e front de portaria lendo a camera (Fase 3).
