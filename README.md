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
| **P4** | O gateway reenvia a mesma notificacao | `INSERT ... ON CONFLICT DO NOTHING` em `payment_event` | [`PaymentWebhookService`](src/main/java/br/com/portaria/payment/PaymentWebhookService.java) |

Os quatro estao resolvidos. A especificacao esta em
[SPEC.md](SPEC.md); as regras de trabalho no repositorio, em
[CLAUDE.md](CLAUDE.md).

## Stack

Java 25 · Spring Boot 4.1.1 · PostgreSQL 16 · Flyway · Spring Data JPA com
`ddl-auto=validate` · springdoc-openapi · ZXing · JUnit 5 + Testcontainers

> O SPEC fixa Java 21 + Spring Boot 3.3.x. Este repositorio roda em Java 25 com
> Boot 4.1.1 por decisao de ambiente. O desvio, e as pegadinhas que ele traz,
> estao registrados no [CLAUDE.md](CLAUDE.md).

## Rodando

Precisa apenas de **Docker**. Nao e necessario ter Java instalado.

```bash
cp .env.example .env    # e troque os tres segredos por valores aleatorios
docker compose up       # banco e aplicacao
```

Para desenvolver com a aplicacao pela IDE, subindo so o banco:

```bash
docker compose up -d db
export $(grep -v '^#' .env | xargs)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Os segredos vem do `.env`, que o Compose le sozinho. Nao ha valor padrao: se
faltar, o Compose para com a mensagem dizendo o que fazer, em vez de subir com
um segredo previsivel.

- **Interface: http://localhost:8080** — login, painel do organizador,
  compra com QR e tela de portaria
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

91 testes, todos contra **Postgres real** via Testcontainers — o SPEC proibe H2
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

### Deploy

**Este projeto nao esta publicado em lugar nenhum, de proposito.** Ele e uma JVM
com Postgres, e nao existe combinacao gratuita que sustente isso com dignidade:
o Postgres gratuito do Render expira em 30 dias, e o servico web hiberna em 15
minutos, levando cerca de um minuto para acordar. Um link de demonstracao que
demora um minuto — ou que esta fora do ar daqui a tres meses — vale menos que
nenhum link.

O `fly.toml` no repositorio e a configuracao real que o deploy usaria: uma
instancia em `gru`, `auto_stop_machines` desligado porque a JVM leva ~10s para
subir, `grace_period` de 45s no health check pelo mesmo motivo, e
`MaxRAMPercentage` para a JVM enxergar a memoria do container e nao a do host.

Para publicar, o caminho e criar o app, provisionar um Postgres e definir
`DB_URL` (em JDBC — o `DATABASE_URL` dos provedores vem como `postgres://`, que
o Spring nao aceita), `DB_USER`, `DB_PASSWORD`, `QR_SECRET`, `JWT_SECRET`,
`WEBHOOK_SECRET` e o par `APP_BOOTSTRAP_ORGANIZER_EMAIL` / `_PASSWORD`.

**Producao nao usa o perfil `dev`**, entao nenhum dado de demonstracao e criado:
a unica conta que nasce e a do bootstrap.

## Interface

Tres telas, servidas pelo proprio Spring a partir de `src/main/resources/static`.
HTML, CSS e JavaScript sem framework e sem build: o back-end e o assunto deste
projeto, e um bundler aqui so acrescentaria passo de build, CORS e um segundo
artefato para versionar.

| Tela | O que faz |
|---|---|
| `/` | Login. Emite o JWT e manda cada papel para a sua tela |
| `/organizador.html` | Eventos, publicacao, lotes e o painel com receita e presenca |
| `/comprador.html` | Compra, pagamento simulado e o QR do ingresso |
| `/portaria.html` | Le a camera e responde verde ou vermelho em tela cheia |

Decisoes de design, com o motivo:

- **Variancia baixa de proposito.** Formulario e tabela pedem previsibilidade;
  composicao assimetrica atrapalha quem esta trabalhando. A inovacao aqui esta
  na interacao da portaria, nao no layout.
- **Fonte do sistema.** A portaria roda no celular de quem esta no evento,
  muitas vezes em rede ruim. Webfont ali custa primeira pintura e risco de texto
  invisivel enquanto baixa.
- **Claro e escuro** via `prefers-color-scheme`, com tokens semanticos.
- **Contraste WCAG AA verificado com calculo, nao a olho.** O azul original do
  botao dava 3.16:1 com texto branco, abaixo do minimo de 4.5:1; foi escurecido
  ate 5.37:1. O verde e o vermelho do veredito subiram para 5.05:1 e 6.40:1.
- **Alvo de toque de 44px** em todo controle, porque a portaria e usada com o
  polegar, de pe.
- **Movimento so como retorno de acao** (botao afunda, veredito entra), sempre
  atras de `prefers-reduced-motion`.

Tres detalhes que a interface obrigou a resolver:

- **O PNG do QR exige `Authorization`**, e o navegador nao manda cabecalho em
  `<img src>`. A imagem vem por `fetch`, vira `Blob` e depois object URL.
- **`getUserMedia` so funciona em contexto seguro** — HTTPS ou `localhost`.
  Aberta pelo IP da rede local, a camera e bloqueada pelo navegador, entao a tela
  diz isso explicitamente e oferece entrada manual do codigo, em vez de mostrar
  um leitor morto sem explicacao.
- **A camera dispara varias leituras por segundo.** Sem uma trava enquanto o
  resultado esta na tela, uma unica pessoa geraria dezenas de requisicoes de
  check-in — e todas menos a primeira voltariam 409.

O retorno da portaria ocupa a tela inteira porque quem opera olha de relance,
com o celular na mao e fila na frente: cor e o unico canal que funciona assim.

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
| POST | `/api/v1/orders/{publicId}/pay` | confirmacao simulada — 200 / 409 |
| POST | `/api/v1/orders/{publicId}/cancel` | RN-08 — 200 / 409 |
| GET | `/api/v1/orders/{publicId}` | 200 com os ingressos / 404 |
| GET | `/api/v1/tickets/{publicId}` | 200 com o `code` assinado / 404 |
| GET | `/api/v1/tickets/{publicId}/qr` | PNG 300x300 |
| POST | `/api/v1/checkins` | 200 GRANTED / 409 / 422 |
| GET | `/api/v1/events/{eventPublicId}/stats` | painel do organizador — 200 / 404 |
| POST | `/api/v1/auth/login` | emite o JWT — 200 / 401 |
| POST | `/api/v1/webhooks/payments` | idempotente — 200 / 401 |

**Toda rota de negocio exige `Authorization: Bearer <token>`.** Ficam abertas
apenas o login, `/actuator/health` e a documentacao. Contas do perfil dev:
`organizador@exemplo.com`, `portaria@exemplo.com` e `comprador@exemplo.com`,
senha `portaria-dev-2026` — dado de demonstracao, nunca de producao.

Em producao nao ha auto-cadastro: o primeiro organizador vem de
`APP_BOOTSTRAP_ORGANIZER_EMAIL` e `APP_BOOTSTRAP_ORGANIZER_PASSWORD`.

### Freio de forca bruta

O login conta tentativas falhas e devolve `429` com `Retry-After`. Duas
contagens, com propositos diferentes: por **(IP, e-mail)**, que protege uma
conta de ter a senha adivinhada, e por **IP**, que pega o atacante variando o
e-mail a cada tentativa — caso que a primeira contagem nao enxerga.

Tres decisoes:

- **A checagem vem antes do BCrypt.** O hash e lento de proposito; sem o freio,
  cada tentativa custa muito mais CPU nossa do que do atacante.
- **Conta inexistente tambem e bloqueada.** Se so as existentes travassem, o
  bloqueio viraria o oraculo que a mensagem generica de credenciais evita.
- **O bloqueio expira sozinho.** Permanente, o freio seria uma arma: errar a
  senha de alguem cinco vezes deixaria a pessoa de fora ate alguem intervir.

A contagem e em memoria — suficiente para uma instancia, que e como este projeto
roda. Com varias replicas, cada uma teria a sua contagem e o limite efetivo
seria multiplicado; ai o lugar disso e um Redis.

### Quem pode o que

| | ORGANIZER | GATE | BUYER |
|---|---|---|---|
| Criar e publicar evento, criar lote | so os proprios | nao | nao |
| Listar eventos | os proprios, em qualquer situacao | — | so os publicados |
| `/stats` (receita) | so os proprios | **nao** | nao |
| `POST /checkins` | nao | so eventos vinculados | nao |
| Comprar, ver pedido e QR | nao | nao | so os proprios |

Falta de papel devolve **403**. Recurso de outra conta devolve **404**, e nao
403: um 403 confirmaria que aquele identificador existe, permitindo mapear
eventos, pedidos e ingressos alheios so variando o UUID.

`POST /orders/{id}/pay` e o webhook coexistem: o `/pay` e a confirmacao simulada
da Fase 1, util para exercitar o fluxo pela interface sem gateway. Com a
integracao real, ele sai — a emissao de ingressos ja vive num unico lugar
(`OrderService.issueTicketsFor`), chamado pelos dois caminhos.

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

### P4 — notificacao repetida

Gateway de pagamento garante "pelo menos uma entrega", nunca "exatamente uma".
Timeout da nossa ponta, deploy no meio do processamento, retry programado: a
mesma notificacao volta. Sem defesa, o pedido e pago de novo e os ingressos sao
emitidos de novo.

```sql
INSERT INTO payment_event (external_id, order_id, status)
     VALUES (:externalId, :orderId, :status)
ON CONFLICT (external_id) DO NOTHING
```

Uma linha afetada e a primeira entrega; zero e repeticao. Nao e "verificar se
existe e depois inserir" — entre a verificacao e a insercao cabe outra
requisicao, que e exatamente o problema.

Duas decisoes acompanham:

- **O corpo da notificacao nao decide nada.** Ele traz so um identificador; a
  situacao vem de uma consulta ao gateway (`PaymentGateway`, onde o Mercado Pago
  entra sem que o resto mude). Quem posta no webhook e a internet inteira, e um
  corpo dizendo "aprovado" nao prova pagamento.
- **A rota exige um segredo compartilhado** no cabecalho `X-Webhook-Secret`,
  comparado em tempo constante. O gateway nao faz login, e sem isso qualquer um
  marcaria pedidos como pagos com um `curl`.
- **Repeticao responde 200.** Erro faria o gateway reenviar em backoff por horas
  por algo ja resolvido: a resposta diz "recebi", nao "concordo".

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
| tirar `@PreAuthorize` de `/stats` | portaria e comprador passam a ler a receita do evento |
| tirar a checagem de dono do pedido | um comprador le e cancela o pedido de outro |
| tirar o `UNIQUE` e usar "verifica depois insere" | **10 das 20** notificacoes processam: o pedido e pago dez vezes |
| desligar o freio de forca bruta | 5 dos 6 testes quebram; o pior deles: a senha correta entra depois de tentativas ilimitadas |

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
