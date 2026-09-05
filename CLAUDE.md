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

**Status atual: Fase 1 e Fase 2 completas.** Proximo trabalho seria a Fase 3
(Mercado Pago, webhook idempotente e front de portaria), que NAO deve ser
iniciada sem pedido explicito. Proximo trabalho seria a Fase 2 (Spring Security + JWT), que NAO deve ser iniciada sem pedido explicito.

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

## Fase 2 — decisoes

O SPEC dedica um paragrafo a Fase 2. O que ficou decidido:

| Tema | Decisao |
|---|---|
| Etapas | 2: (1) identidade e autenticacao; (2) toda a autorizacao |
| Compra | `POST /orders` exige `BUYER`; o pedido pertence ao usuario autenticado |
| Cadastro | **Nao ha auto-cadastro.** Contas nascem por seed/provisionamento |
| Token | JWT HS256, `JWT_SECRET` no ambiente, 60 min, claim `roles` sem prefixo |
| Senha | `DelegatingPasswordEncoder` (BCrypt com prefixo `{bcrypt}`) |

**Como nascem as contas em producao:** `OrganizerBootstrap` cria o primeiro
ORGANIZER a partir de `app.bootstrap.organizer.email` e `.password` (variaveis de
ambiente). So age se as duas estiverem definidas, exige senha de 12+ caracteres e
nunca sobrescreve conta existente. Nao ha, e nao deve haver, rota publica de
cadastro que aceite papel.

**Convencao de status na autorizacao:** falta de papel devolve **403**; recurso
que existe mas pertence a outra conta devolve **404**. Um 403 no segundo caso
confirmaria a existencia do identificador e permitiria mapear eventos, pedidos e
ingressos alheios variando o UUID.

Spring Security e **7.1.1**, nao o 6 do SPEC: e o que o Boot 4 traz.

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
- **`AccessDeniedException` lancada dentro do service nao chega ao
  `AccessDeniedHandler`.** Ela nasce depois do DispatcherServlet, entao vira 500
  se o `GlobalExceptionHandler` nao a tratar. Foi assim que token de conta
  apagada respondia 500 em vez de 403.
- **`@IdClass` nao aceita `record`.** O JPA le a chave no padrao JavaBean, e
  `event()`/`user()` nao atendem. `EventStaff.EventStaffId` e classe comum.
- **Nao teste JWT adulterado trocando o ULTIMO caractere da assinatura.** Os 32
  bytes do HMAC-SHA256 ocupam 43 caracteres em Base64url e o ultimo carrega so
  4 bits uteis; trocar esse caractere pode alterar apenas os bits ignorados, e o
  token continua valido. Como `iat`/`exp` mudam a cada execucao, o teste vira
  sorteio — passou local e falhou no CI. Edite as claims ou um caractere do meio.
- **`NimbusJwtEncoder` assume RS256.** Com chave HMAC e preciso declarar
  `JwsHeader.with(MacAlgorithm.HS256)` nos parametros, senao todo token falha com
  "Failed to select a JWK signing key".
- **401 e 403 nascem fora do `GlobalExceptionHandler`.** O Spring Security
  responde antes de existir controller; `ProblemDetailSecurityHandlers` garante
  que essas duas respostas tambem sigam a RFC 7807.
- **Ler o PNG do QR de volta exige `DecodeHintType.PURE_BARCODE`.** O detector
  padrao do ZXing e feito para fotos e falha em ~1,5% das imagens sinteticas,
  dependendo do conteudo. Sem a hint, o teste vira um sorteio.

## Interface

`src/main/resources/static`: HTML/CSS/JS sem framework, servido pelo Spring.
Sem build, sem CORS, um artefato so. Ao criar pagina nova, libere o caminho no
`SecurityConfig` — `anyRequest().authenticated()` fecha tudo por padrao, e isso
vale tambem para arquivo estatico.

- O PNG do QR exige `Authorization`: use `fetch` + `Blob` + object URL, nunca
  `<img src>` direto.
- `getUserMedia` exige HTTPS ou localhost. Para testar a camera no celular, use
  um tunel (`cloudflared tunnel --url http://localhost:8080`); nao adianta abrir
  pelo IP da rede.

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
