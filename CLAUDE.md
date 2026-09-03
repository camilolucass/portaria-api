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

**Status atual: Etapa 2 concluida.** Proxima: Etapa 3 (pedidos e reserva atomica).

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

## Verificacao

```
docker compose up -d
./mvnw verify
./mvnw spring-boot:run
curl http://localhost:8080/actuator/health
```

`QR_SECRET` (>= 32 caracteres) so passa a ser obrigatorio na Etapa 4.
Nunca versione o valor real.
