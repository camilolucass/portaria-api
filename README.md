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
cp .env.example .env          # e troque QR_SECRET e JWT_SECRET por valores aleatorios
docker compose up -d          # Postgres 16 em localhost:5432

export $(grep -v '^#' .env | xargs)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

- Swagger UI: http://localhost:8080/docs
- OpenAPI: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

O perfil `dev` cria um evento publicado e dois lotes no primeiro boot, entao da
para comprar um ingresso pelo Swagger sem nenhum preparo. **Sem o perfil, nenhum
dado de demonstracao e criado** — o seed nao e migration justamente para nao
existir a chance de rodar em producao. Ele e idempotente: reiniciar nao duplica.

### Testes

```bash
./mvnw verify
```

72 testes, todos contra **Postgres real** via Testcontainers — o SPEC proibe H2
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
| GET | `/api/v1/events/{eventPublicId}/stats` | painel do organizador — 200 / 404 |
| POST | `/api/v1/auth/login` | emite o JWT — 200 / 401 |

**Toda rota de negocio exige `Authorization: Bearer <token>`.** Ficam abertas
apenas o login, `/actuator/health` e a documentacao. Contas do perfil dev:
`organizador@exemplo.com`, `portaria@exemplo.com` e `comprador@exemplo.com`,
senha `portaria-dev-2026` — dado de demonstracao, nunca de producao.

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
| devolver estoque sem reivindicar o pedido | 8 instancias do job devolvem o mesmo pedido: estoque cai de 9 para 1 em vez de 5 |

A ultima mutacao passou despercebida na primeira tentativa: a linha estava
escrita, como o SPEC pede, mas nenhum teste dependia dela. O teste de
concorrencia passou a exigir que toda recusa informe o horario, e so entao a
mutacao quebrou.

## Estatisticas e indices

`GET /events/{id}/stats` resolve o painel inteiro em duas consultas agregadas —
nada de carregar ingressos em memoria para contar em Java. A migration `V4`
acrescenta `ticket(batch_id, status)` e `purchase_order(batch_id, status)`.

Medido com 50 eventos, 100 lotes e 100 mil ingressos, mesma consulta e mesmo
dataset, so alternando o uso dos indices:

| | Tempo | Linhas lidas |
|---|---|---|
| sem indice (seq scan) | 17,4 ms | 100.000 |
| com `idx_ticket_batch_status` | 2,4 ms | 1.000 por lote |

A V4 tambem remove `idx_ticket_batch` da V1: era prefixo exato do novo indice
composto, entao o planner nunca mais o escolheria e ele so custava escrita.

`totalRevenueCents` e `long`, nao `int`. A secao 2 do SPEC fixa dinheiro em
centavos `int`, o que vale para valores individuais; um agregado em `int`
estoura em R$ 21,4 milhoes, e overflow de inteiro nao levanta erro em Java.

> Esta rota devolve receita. Na Fase 2 ela e a primeira a exigir o papel
> `ORGANIZER` — o SPEC diz que o operador de portaria nunca ve dado financeiro.

## Estado

Fase 1 completa — as cinco etapas da secao 11 do SPEC e todos os contratos da
secao 6, incluindo `/stats`. Os dez criterios de aceite (TC-01 a TC-10) cobertos.

Proximas fases, ainda nao implementadas: Spring Security + JWT (Fase 2);
Mercado Pago com webhook idempotente e front de portaria lendo a camera (Fase 3).
