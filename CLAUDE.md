# CLAUDE.md — regras de trabalho neste repositorio

A fonte da verdade e o `SPEC.md`. Leia por completo antes de qualquer alteracao.

## Regra numero 1 — nao avance de etapa

O trabalho e executado **uma etapa por vez**, na ordem da secao 11 do SPEC.
Nunca implemente codigo de uma etapa futura, nem "preparando o terreno".
Se a tarefa pedida pertence a outra etapa, pare e diga a qual etapa ela pertence.

| Etapa | Escopo | Commit |
|---|---|---|
| 1 | Projeto Maven, docker-compose, migration V1, entidades, enums, repositories vazios. App sobe com `ddl-auto=validate`. | `feat: estrutura inicial e schema` |
| 2 | CRUD de eventos e lotes, publish, `GlobalExceptionHandler` com `ProblemDetail`, Bean Validation. TC-10. | `feat: eventos e lotes` |
| 3 | `OrderService` (7.1), expiracao (7.4), emissao de ingressos. TC-01, TC-07, TC-08, TC-09. | `feat: pedidos com reserva atomica de estoque` |
| 4 | `QrCodeSigner` (7.2), ZXing, `CheckinService` (7.3). TC-02 a TC-06. | `feat: qr assinado e check-in atomico` |
| 5 | Swagger, seed `V2`, Actuator, README, GitHub Actions, Dockerfile. | — |

**Status atual: Fase 1 completa (Etapas 1 a 5 + `/stats` da secao 6).** Proximo trabalho seria a Fase 2 (Spring Security + JWT), que NAO deve ser iniciada sem pedido explicito.

## Fora do escopo da Fase 1 (secao 10 do SPEC)

Autenticacao/JWT, gateway de pagamento e webhook, front-end, e-mail, upload,
reembolso, meia-entrada, assento marcado, transferencia de titularidade.

## Convencoes inegociaveis

- Pacote raiz `br.com.portaria`, organizado **por feature**, nunca por camada.
- Codigo, tabelas, colunas e rotas em **ingles**; mensagens de erro em **portugues**.
- Rotas sob `/api/v1`.
- Dinheiro em **centavos**, `int`/`integer`. Nunca `double`.
- Datas em `LocalDateTime`. Container com `TZ=America/Sao_Paulo`.
- Toda entidade tem PK `bigserial` **e** `public_id UUID`. ID interno nunca aparece na API.
- DTOs sao `record`. Entidade JPA nunca e serializada em resposta.
- Lombok apenas `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`.
  **`@Data` em entidade JPA e proibido.**
- Enums com `@Enumerated(EnumType.STRING)`. Nunca `ORDINAL`.
- Todo schema nasce em migration Flyway. `ddl-auto` fica em `validate`, sempre.
- `open-in-view: false` fica desligado. Nao religue para "resolver" LazyInitializationException.
- Testes com **Testcontainers e Postgres real**. H2 e proibido, inclusive em teste.

## Desvio deliberado da stack do SPEC

O SPEC fixa Java 21 + Spring Boot 3.3.x. Esta maquina tem apenas o **JDK 25**, e
por decisao do dono do projeto o build usa:

- **Java 25**
- **Spring Boot 4.1.1** (Spring Framework 7 / Hibernate 7 — as unicas linhas com
  suporte oficial a JDK 25)
- springdoc-openapi **3.1.0** (linha compativel com Boot 4; o `2.6.x` do SPEC e para Boot 3)

Pegadinhas do Boot 4 ja resolvidas, para nao serem reintroduzidas:

- `flyway-core` sozinho **nao** ativa a autoconfiguracao. Use `spring-boot-starter-flyway`.
  Sem isso o app sobe, a migration nao roda, e o erro aparece como
  `Schema validation: missing table [buyer]`.
- Modulos do Testcontainers foram renomeados: `testcontainers-postgresql` e
  `testcontainers-junit-jupiter` (nao mais `postgresql` / `junit-jupiter`).
- `@AutoConfigureMockMvc` vive em `org.springframework.boot.webmvc.test.autoconfigure`
  e exige `spring-boot-starter-webmvc-test`.
- O `ObjectMapper` padrao e o do **Jackson 3** (`tools.jackson.databind`), nao o
  `com.fasterxml.jackson.databind`.

Todo o resto do SPEC vale sem alteracao. Se for pedido para voltar ao Java 21,
troque o parent para `3.3.x`, o springdoc para `2.6.x` e `java.version` para 21.

## Desvios do SPEC decididos durante a Fase 1

- **Tabela `order_holder` (migration `V2`).** A secao 6 recebe os `holders` na
  criacao do pedido, mas a RN-07 so permite gerar o ingresso na transicao para
  PAID — e o schema da secao 4 nao tem onde guardar os titulares nesse intervalo.
  A tabela existe so para esse periodo; no pagamento cada linha vira um ticket.
  Consequencia: o seed da Etapa 5 passa a ser `V3__seed_demo.sql`.
- **Seed fora do Flyway.** O SPEC pede o seed como migration, mas migration roda
  em todo banco que o Flyway alcanca, producao inclusive — e remover depois uma
  migration ja aplicada quebra a validacao. Virou `DemoDataSeeder`, bean com
  `@Profile("dev")` e idempotente. A `V3` foi removida e a `V4` **nao** foi
  renumerada: nunca renumere migration ja publicada; o Flyway aceita lacunas.
- **Pacote `stats/`, fora da secao 3.** `GET /events/{id}/stats` esta na secao 6
  (contratos da Fase 1) mas a secao 11 nao o atribuiu a nenhuma etapa, e a secao 3
  nao lista pacote para ele. Nao pode morar em `event/`: `batch` ja depende de
  `event`, entao `event -> batch` fecharia ciclo. O pacote `stats/` depende de
  `event`, `batch` e `ticket`, todas as setas para dentro.
- **Rota `POST /orders/{publicId}/cancel`.** A RN-08 e regra da Fase 1, mas a
  secao 6 nao lista endpoint de cancelamento. A regra esta em
  `OrderService.cancel` e a rota foi exposta; se o SPEC for tratado como fechado,
  remover o metodo do controller basta.

## Armadilhas ja pagas — nao reintroduzir

- **O job de expiracao roda em toda instancia.** Devolver estoque direto, como a
  leitura ingenua do 7.4 sugere, faz o saldo voltar uma vez por replica. O pedido
  e reivindicado antes por `OrderRepository.markExpired`, um UPDATE condicional;
  so quem afeta a linha devolve estoque. O finder ordena por id de proposito,
  para as instancias tomarem os locks na mesma sequencia.
- **`mvn` nao remove recurso deletado de `target/classes`.** Apagar uma migration
  e rodar `verify` sem `clean` continua embutindo o arquivo antigo no jar. Ao
  mexer em migrations, rode `./mvnw clean verify`.
- **Ler o PNG do QR de volta exige `DecodeHintType.PURE_BARCODE`.** O detector
  padrao do ZXing e feito para fotos e falha em ~1,5% das imagens sinteticas,
  dependendo do conteudo. Sem a hint, o teste vira um sorteio.

## Verificacao

```
docker compose up -d
./mvnw verify
./mvnw spring-boot:run
curl http://localhost:8080/actuator/health
```

`QR_SECRET` (>= 32 caracteres) e obrigatorio desde a Etapa 4. Fica em `.env`,
que esta no `.gitignore` — nunca versione o valor real. Para rodar:

```
export QR_SECRET=$(sed 's/^QR_SECRET=//' .env)
./mvnw spring-boot:run
```

Nos testes o segredo vem de `AbstractIntegrationTest` via `@DynamicPropertySource`.
Trocar o `QR_SECRET` invalida todos os QR ja emitidos — e o comportamento
correto, mas em producao significa reemitir ingressos.
