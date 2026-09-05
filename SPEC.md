# Portaria — Especificação Técnica v1

## 1. Objetivo

API REST para **venda de ingressos e controle de entrada por QR Code** em eventos.

O sistema resolve quatro problemas que um CRUD não resolve:

| # | Problema | Onde é resolvido |
|---|---|---|
| P1 | Vender mais ingressos do que o lote comporta quando dois compradores chegam juntos | `UPDATE` condicional atômico + `CHECK` no banco |
| P2 | Alguém gerar um QR Code falso | Código assinado com HMAC-SHA256 |
| P3 | O mesmo ingresso entrar duas vezes, mesmo em duas portarias simultâneas | `UPDATE ... WHERE status = 'ISSUED'` verificando linhas afetadas |
| P4 | O gateway de pagamento reenviar a mesma notificação | Tabela de eventos processados com constraint `UNIQUE` |

Fase 1 entrega P1, P2 e P3. P4 entra na Fase 3.

---

## 2. Stack fixada

Não substitua nenhum item sem que seja pedido.

- **Java 21** (records, text blocks, pattern matching)
- **Spring Boot 3.3.x**, build **Maven**
- **PostgreSQL 16** — sem H2, nem em teste
- **Spring Data JPA** com `spring.jpa.hibernate.ddl-auto=validate`
- **Flyway** — todo schema nasce em migration
- **Bean Validation** (`spring-boot-starter-validation`)
- **springdoc-openapi** `2.6.x` (`springdoc-openapi-starter-webmvc-ui`)
- **ZXing** `3.5.3` (`core` + `javase`) para renderizar o PNG do QR
- **Spring Boot Actuator**
- **JUnit 5 + Mockito + Testcontainers** (módulo `postgresql`)
- **Docker Compose** para o banco local
- **Lombok** permitido apenas para `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`. **Proibido** `@Data` em entidade JPA.

### Convenções

- **Código, nomes de tabela, coluna e rota em inglês. Mensagens de erro em português.** Sem mistura dentro de um mesmo identificador.
- Pacote raiz: `br.com.portaria`
- Toda rota sob `/api/v1`
- Valores monetários em **centavos**, tipo `int` / `integer`. Nunca `double`.
- Datas: `LocalDateTime` em toda a aplicação; container com `TZ=America/Sao_Paulo`.
- Toda entidade tem PK interna `bigserial` **e** um `public_id UUID` exposto na API. IDs internos nunca aparecem em resposta nem em URL.
- DTOs são `record`. Entidade JPA nunca é serializada em resposta.

---

## 3. Estrutura de pacotes

```
br.com.portaria
├── event/          Event, EventStatus, EventRepository, EventService, EventController
├── batch/          TicketBatch, TicketBatchRepository, TicketBatchService, TicketBatchController
├── order/          PurchaseOrder, OrderStatus, Buyer, OrderRepository, OrderService, OrderController
│                   OrderExpirationJob
├── ticket/         Ticket, TicketStatus, TicketRepository, TicketService, TicketController
│                   QrCodeSigner, QrCodeRenderer
├── checkin/        CheckinService, CheckinController, CheckinResult
├── shared/
│   ├── exception/  BusinessException e subclasses, GlobalExceptionHandler
│   └── config/     OpenApiConfig, SchedulingConfig
└── PortariaApplication.java
```

Organização **por feature**, não por camada técnica. Cada pacote é coeso e as dependências apontam para dentro (`controller → service → repository`), nunca ao contrário.

---

## 4. Modelo de dados

Migration `V1__initial_schema.sql`:

```sql
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
    -- rede de segurança final contra oversell: o banco recusa, aconteça o que acontecer
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
```

> `purchase_order` e não `order`: `ORDER` é palavra reservada em SQL.

### Enums

```java
public enum EventStatus  { DRAFT, PUBLISHED, FINISHED, CANCELLED }
public enum OrderStatus  { PENDING, PAID, EXPIRED, CANCELLED }
public enum TicketStatus { ISSUED, USED, CANCELLED }
```

Mapeados com `@Enumerated(EnumType.STRING)`. Nunca `ORDINAL`.

---

## 5. Regras de negócio

**Evento e lote**

- **RN-01** — Só um evento `PUBLISHED` aceita venda.
- **RN-02** — Lote só vende dentro de `[sales_start, sales_end]` e o `sales_end` não pode ultrapassar `event.starts_at`.
- **RN-03** — Um pedido compra ingressos de **um único lote**. Compra em lotes diferentes = pedidos diferentes.

**Pedido**

- **RN-04** — Criar pedido **reserva** o estoque imediatamente (incrementa `sold_quantity`), mesmo antes do pagamento. Reserva sem estoque disponível é recusada com **409**.
- **RN-05** — Pedido nasce `PENDING` com `expires_at = now() + 15 minutos`.
- **RN-06** — Pedido `PENDING` vencido vira `EXPIRED` e **devolve o estoque ao lote**. Job a cada 60s.
- **RN-07** — Os ingressos são gerados **apenas** na transição para `PAID`, um por unidade de `quantity`.
- **RN-08** — Cancelar um pedido `PAID` cancela também todos os seus ingressos (`CANCELLED`) e devolve o estoque, **exceto** os que já estiverem `USED`.

**Ingresso e check-in**

- **RN-09** — O conteúdo do QR é `{ticket.public_id}.{assinatura}`, com assinatura HMAC-SHA256 sobre o `public_id`, em Base64 URL-safe sem padding.
- **RN-10** — Assinatura inválida ⇒ **422**, sem revelar se o ingresso existe.
- **RN-11** — Check-in só é aceito se o ingresso estiver `ISSUED`. Já utilizado ⇒ **409** informando `checkedInAt`. Cancelado ⇒ **409**.
- **RN-12** — Check-in só dentro da janela `[event.gate_opens_at, event.ends_at]`. Fora ⇒ **422**.
- **RN-13** — O check-in é **atômico**: entre N tentativas simultâneas com o mesmo código, exatamente uma pode ser aceita. Isso não é uma meta de desempenho, é requisito funcional testado.

---

## 6. Contratos da API — Fase 1

Erros seguem **RFC 7807** (`ProblemDetail`), com `title` em português.

### Eventos

`POST /api/v1/events` → **201**
```json
{ "name": "Festa Universitária 2026", "description": "Open bar",
  "venue": "Centro de Eventos, Orleans/SC",
  "gateOpensAt": "2026-10-10T21:00:00",
  "starts_at": "2026-10-10T22:00:00", "endsAt": "2026-10-11T04:00:00" }
```
```json
{ "id": "9f1c...", "name": "...", "status": "DRAFT", ... }
```

- `POST /api/v1/events/{publicId}/publish` → **200** — `DRAFT → PUBLISHED`
- `GET  /api/v1/events` → **200** paginado (`Pageable`)
- `GET  /api/v1/events/{publicId}` → **200** / **404**

### Lotes

`POST /api/v1/events/{eventPublicId}/batches` → **201**
```json
{ "name": "1º lote", "priceCents": 4500, "totalQuantity": 200,
  "salesStart": "2026-09-10T00:00:00", "salesEnd": "2026-10-05T23:59:00" }
```

`GET /api/v1/events/{eventPublicId}/batches` → **200**, cada item com `availableQuantity = totalQuantity - soldQuantity`.

### Pedidos

`POST /api/v1/orders` → **201** · **409** lote esgotado · **422** fora da janela de venda
```json
{ "batchId": "3ab2...", "quantity": 2,
  "buyer": { "name": "Lucas", "email": "lucas@exemplo.com", "document": "12345678900" },
  "holders": [ { "name": "Lucas Camilo", "document": "12345678900" },
               { "name": "Ana Souza",    "document": "98765432100" } ] }
```
`holders.size()` deve ser igual a `quantity` — senão **400**.

- `POST /api/v1/orders/{publicId}/pay` → **200** — **Fase 1 apenas**: simula a confirmação, dispara RN-07. Na Fase 3 esta rota é substituída pelo webhook.
- `GET  /api/v1/orders/{publicId}` → **200** com a lista de ingressos emitidos.

### Ingressos

- `GET /api/v1/tickets/{publicId}` → **200** com `code` (a string do QR).
- `GET /api/v1/tickets/{publicId}/qr` → **200** `image/png`, QR 300×300 gerado por ZXing.

### Check-in

`POST /api/v1/checkins` → **200** · **409** · **422**
```json
{ "code": "3f9c8a12-....OJf3kQ2mZ...", "operator": "portaria-1" }
```
**200**
```json
{ "result": "GRANTED", "holderName": "Ana Souza",
  "batchName": "1º lote", "eventName": "Festa Universitária 2026",
  "checkedInAt": "2026-10-10T22:14:31" }
```
**409**
```json
{ "type": "about:blank", "title": "Ingresso já utilizado", "status": 409,
  "detail": "Este ingresso foi validado em 10/10/2026 às 22:03 por portaria-2." }
```

### Estatísticas

`GET /api/v1/events/{publicId}/stats` → **200**
```json
{ "totalIssued": 180, "totalCheckedIn": 143, "totalRevenueCents": 810000,
  "byBatch": [ { "name": "1º lote", "sold": 200, "checkedIn": 143 } ] }
```

---

## 7. Algoritmos críticos

Estes quatro trechos são o núcleo. Implemente-os exatamente como especificado.

### 7.1 Reserva de estoque sem oversell (P1)

Ler e depois somar é o erro clássico — entre a leitura e a escrita cabe outra transação. A soma tem de acontecer **dentro** do `UPDATE`, e a decisão vem do número de linhas afetadas.

```java
public interface TicketBatchRepository extends JpaRepository<TicketBatch, Long> {

    @Modifying
    @Query("""
        UPDATE TicketBatch b
           SET b.soldQuantity = b.soldQuantity + :quantity
         WHERE b.id = :batchId
           AND b.soldQuantity + :quantity <= b.totalQuantity
        """)
    int reserve(@Param("batchId") Long batchId, @Param("quantity") int quantity);

    @Modifying
    @Query("""
        UPDATE TicketBatch b
           SET b.soldQuantity = b.soldQuantity - :quantity
         WHERE b.id = :batchId AND b.soldQuantity >= :quantity
        """)
    int release(@Param("batchId") Long batchId, @Param("quantity") int quantity);
}
```

```java
if (batchRepository.reserve(batch.getId(), request.quantity()) == 0) {
    throw new SoldOutException("Lote esgotado ou quantidade indisponível");
}
```

Sem lock explícito, sem `@Version`, sem retry: o `UPDATE` condicional já é serializado pelo banco na linha.

### 7.2 Assinatura do QR (P2)

```java
@Component
public class QrCodeSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private final SecretKeySpec key;

    public QrCodeSigner(@Value("${app.qr.secret}") String secret) {
        if (secret == null || secret.length() < 32)
            throw new IllegalStateException("app.qr.secret deve ter ao menos 32 caracteres");
        this.key = new SecretKeySpec(secret.getBytes(UTF_8), ALGORITHM);
    }

    public String sign(UUID ticketPublicId) {
        return ticketPublicId + "." + mac(ticketPublicId.toString());
    }

    public UUID verifyAndExtract(String code) {
        String[] parts = code.split("\\.", 2);
        if (parts.length != 2) throw new InvalidTicketCodeException("Código inválido");

        // comparação em tempo constante: evita timing attack
        if (!MessageDigest.isEqual(mac(parts[0]).getBytes(UTF_8), parts[1].getBytes(UTF_8)))
            throw new InvalidTicketCodeException("Código inválido");

        try { return UUID.fromString(parts[0]); }
        catch (IllegalArgumentException e) { throw new InvalidTicketCodeException("Código inválido"); }
    }

    private String mac(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(data.getBytes(UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar o código", e);
        }
    }
}
```

As três mensagens de erro são idênticas de propósito: o atacante não deve descobrir se errou o formato, a assinatura ou o UUID.

`app.qr.secret` vem de `${QR_SECRET}`. **Nunca** com valor padrão em arquivo versionado.

### 7.3 Check-in atômico (P3)

Mesmo princípio de 7.1: a condição vai dentro do `UPDATE`.

```java
@Modifying(clearAutomatically = true)
@Query("""
    UPDATE Ticket t
       SET t.status = :used, t.checkedInAt = :now, t.checkedInBy = :operator
     WHERE t.id = :id AND t.status = :issued
    """)
int checkIn(@Param("id") Long id, @Param("now") LocalDateTime now,
            @Param("operator") String operator,
            @Param("used") TicketStatus used, @Param("issued") TicketStatus issued);
```

```java
@Transactional
public CheckinResult checkIn(String code, String operator) {
    UUID publicId = signer.verifyAndExtract(code);            // RN-09, RN-10
    Ticket ticket = ticketRepository.findByPublicId(publicId)
            .orElseThrow(() -> new InvalidTicketCodeException("Código inválido"));

    validateGateWindow(ticket);                                // RN-12

    int updated = ticketRepository.checkIn(
            ticket.getId(), LocalDateTime.now(), operator, USED, ISSUED);

    if (updated == 0) {                                        // RN-11
        Ticket current = ticketRepository.findById(ticket.getId()).orElseThrow();
        throw new TicketAlreadyUsedException(current.getCheckedInAt(), current.getCheckedInBy());
    }
    return CheckinResult.granted(ticket);
}
```

`clearAutomatically = true` é obrigatório: sem ele o `findById` seguinte devolve a versão em cache do contexto de persistência, com o status antigo.

### 7.4 Expiração de pedidos (RN-06)

```java
@Scheduled(fixedDelay = 60_000)
@Transactional
public void expirePendingOrders() {
    List<PurchaseOrder> expired =
        orderRepository.findByStatusAndExpiresAtBefore(PENDING, LocalDateTime.now());

    for (PurchaseOrder order : expired) {
        batchRepository.release(order.getBatch().getId(), order.getQuantity());
        order.setStatus(EXPIRED);
    }
    if (!expired.isEmpty()) log.info("Expirados {} pedidos, estoque devolvido", expired.size());
}
```

Habilite com `@EnableScheduling` em `SchedulingConfig`.

---

## 8. Critérios de aceite

Estes testes são a definição de pronto da Fase 1. Todos com **Testcontainers e Postgres real** — H2 não reproduz o comportamento concorrente.

| ID | Teste | Resultado esperado |
|---|---|---|
| **TC-01** | Lote com 10 vagas, 20 threads criando pedido de 1 ingresso ao mesmo tempo | exatamente **10** sucessos, 10 `SoldOutException`, `sold_quantity = 10` |
| **TC-02** | 1 ingresso `ISSUED`, 20 threads fazendo check-in do mesmo código simultaneamente | exatamente **1** `GRANTED`, 19 `TicketAlreadyUsedException` |
| **TC-03** | Código com o último caractere da assinatura alterado | **422**, mensagem genérica |
| **TC-04** | Código bem formado com UUID inexistente | **422**, mesma mensagem do TC-03 |
| **TC-05** | Check-in antes de `gate_opens_at` | **422** |
| **TC-06** | Check-in de ingresso `CANCELLED` | **409** |
| **TC-07** | Pedido `PENDING` com `expires_at` no passado, após o job | `EXPIRED` e `sold_quantity` de volta ao valor anterior |
| **TC-08** | Pedido pago | gera exatamente `quantity` ingressos, um por `holder` |
| **TC-09** | `quantity = 2` com 3 `holders` | **400** |
| **TC-10** | Compra em lote fora da janela de vendas | **422** |

Modelo para os testes de concorrência (TC-01 e TC-02):

```java
int threads = 20;
var pool = Executors.newFixedThreadPool(threads);
var start = new CountDownLatch(1);
var done  = new CountDownLatch(threads);
var granted = new AtomicInteger();

for (int i = 0; i < threads; i++) {
    pool.submit(() -> {
        try {
            start.await();
            checkinService.checkIn(code, "portaria-" + Thread.currentThread().getId());
            granted.incrementAndGet();
        } catch (TicketAlreadyUsedException ignored) {
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finally { done.countDown(); }
    });
}
start.countDown();                                   // largada simultânea
assertTrue(done.await(15, TimeUnit.SECONDS));
pool.shutdown();

assertThat(granted.get()).isEqualTo(1);
```

---

## 9. Configuração

`application.yml`
```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/portaria}
    username: ${DB_USER:portaria}
    password: ${DB_PASSWORD:portaria}
  jpa:
    hibernate.ddl-auto: validate
    open-in-view: false          # obrigatório: força o DTO a ser montado no service
  flyway.enabled: true

app:
  qr.secret: ${QR_SECRET}
  order.expiration-minutes: 15

management.endpoints.web.exposure.include: health,info,metrics
springdoc.swagger-ui.path: /docs
```

`docker-compose.yml`
```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: portaria
      POSTGRES_USER: portaria
      POSTGRES_PASSWORD: portaria
      TZ: America/Sao_Paulo
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]
volumes:
  pgdata:
```

`open-in-view: false` não é detalhe de estilo: com ele ligado, o Hibernate mantém a sessão aberta até a resposta e mascara erros de mapeamento que só aparecem em produção.

---

## 10. Fora do escopo da Fase 1

Não implemente. Não crie classe, coluna ou rota "preparando o terreno".

- Autenticação, JWT, usuários, papéis → **Fase 2**
- Integração com gateway de pagamento e webhook → **Fase 3**
- Front-end de qualquer tipo → **Fase 3**
- Envio de e-mail, upload de imagem, reembolso, meia-entrada, assento marcado, transferência de titularidade

---

## 11. Ordem de execução — Fase 1 (4 dias)

Cada etapa termina com testes verdes antes da seguinte.

**Etapa 1 — Fundação**
Projeto Maven, `docker-compose up`, migration `V1`, entidades e enums, repositories vazios, aplicação sobe com `ddl-auto=validate` sem erro. Commit: `feat: estrutura inicial e schema`.

**Etapa 2 — Eventos e lotes**
CRUD, publicação do evento, `GlobalExceptionHandler` com `ProblemDetail`, Bean Validation. Testes TC-10. Commit: `feat: eventos e lotes`.

**Etapa 3 — Pedidos e reserva**
`OrderService` com 7.1, expiração 7.4, emissão de ingressos. Testes **TC-01, TC-07, TC-08, TC-09**. Commit: `feat: pedidos com reserva atomica de estoque`.

**Etapa 4 — QR e check-in**
`QrCodeSigner` (7.2), renderização ZXing, `CheckinService` (7.3). Testes **TC-02 a TC-06**. Commit: `feat: qr assinado e check-in atomico`.

**Etapa 5 — Acabamento**
Swagger com `@Operation` em toda rota, seed `V2__seed_demo.sql` com 1 evento publicado e 2 lotes, Actuator, README, GitHub Actions (`mvn verify`), Dockerfile multi-stage.

---

## 12. Fases seguintes — resumo

**Fase 2 (3 dias) — Segurança**
Spring Security 6 + JWT. Papéis `ORGANIZER`, `GATE`, `BUYER`. Organizador só enxerga os próprios eventos; operador de portaria só acessa `POST /checkins` dos eventos aos quais está vinculado (tabela `event_staff`) e nunca vê dado financeiro. `checked_in_by` passa a guardar o usuário autenticado.

**Fase 3 (4 dias) — Pagamento e portaria**
Mercado Pago em sandbox: criação de preferência, `POST /webhooks/mercadopago` **idempotente** (tabela `payment_event` com `UNIQUE(external_id)`; nunca confiar no corpo da notificação — consultar a API para obter o status real). `POST /orders/{id}/pay` é removida. Front: página de portaria lendo a câmera (`html5-qrcode`), verde/vermelho em tela cheia, e painel do organizador com os dados de `/stats`.
