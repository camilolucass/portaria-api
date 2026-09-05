# Decisões técnicas

Registro do que foi decidido durante o projeto, onde ele se afasta da
especificação e quais armadilhas já custaram tempo. A especificação em si está
no [SPEC.md](SPEC.md).

## Como o projeto foi construído

Uma etapa por vez, com os testes da etapa anterior passando antes de começar a
seguinte. Cada etapa virou um commit:

| Etapa | Entrega |
|---|---|
| 1 | Schema, entidades e migrations. Aplicação sobe com `ddl-auto=validate` |
| 2 | Eventos e lotes, validação e tratamento de erro em RFC 7807 |
| 3 | Pedidos com reserva de estoque, expiração e emissão de ingressos |
| 4 | QR assinado e check-in |
| 5 | Swagger, seed de demonstração, CI e Dockerfile |
| 6 | Autenticação e autorização por papel |
| 7 | Webhook de pagamento idempotente, interface e limite de tentativas de login |

O que falta é a integração real com o Mercado Pago. O webhook e a idempotência
já existem; a costura para o gateway real é a interface `PaymentGateway`, e
trocar a implementação não muda mais nada no fluxo.

## Convenções

- Pacote raiz `br.com.portaria`, organizado **por assunto**, nunca por camada
- Código, tabelas, colunas e rotas em inglês; mensagens de erro em português
- Todas as rotas sob `/api/v1`
- Dinheiro em **centavos**, `int`. Nunca `double`
- Datas em `LocalDateTime`, container com `TZ=America/Sao_Paulo`
- Toda entidade tem chave interna `bigserial` **e** um `public_id UUID`. O id
  interno nunca aparece na API
- DTOs são `record`. Entidade JPA nunca é serializada em resposta
- Lombok só para `@Getter`, `@Setter`, construtores e `@Builder`.
  `@Data` em entidade JPA é proibido
- Enums com `@Enumerated(EnumType.STRING)`, nunca `ORDINAL`
- Todo schema nasce em migration Flyway, e `ddl-auto` fica em `validate`
- `open-in-view: false`. Não religar para contornar `LazyInitializationException`
- Testes contra PostgreSQL real via Testcontainers. Banco em memória não entra,
  nem em teste

## Onde o projeto se afasta da especificação

**Java 25 e Spring Boot 4**, em vez de Java 21 e Boot 3.3. A máquina de
desenvolvimento só tinha o JDK 25, e o Boot 4 é a primeira linha com suporte
oficial a ele. Junto vieram Spring Security 7 e springdoc 3. Para voltar ao
original: `parent` para `3.3.x`, springdoc para `2.6.x`, `java.version` para 21.

**Tabela `order_holder`.** A especificação recebe os titulares na criação do
pedido, mas só permite emitir o ingresso no pagamento, e o schema não tinha onde
guardá-los nesse intervalo. A tabela existe só para esse período.

**Seed fora do Flyway.** A especificação pede o dado de demonstração como
migration, mas migration roda em todo banco que o Flyway alcança, produção
inclusive. Virou um bean com `@Profile("dev")`, idempotente.

**Pacote `stats/`.** A rota de estatísticas está nos contratos, mas sem pacote
definido. Não podia morar em `event/`: `batch` já depende de `event`, então
`event -> batch` fecharia um ciclo.

**Rota `POST /orders/{id}/cancel`.** A regra de cancelamento existe na
especificação, mas sem endpoint. A regra ficou em `OrderService.cancel` e a rota
foi exposta.

**Convenção de status na autorização:** falta de papel devolve **403**; recurso
que existe mas pertence a outra conta devolve **404**. Um 403 no segundo caso
confirmaria que aquele identificador existe, o que permitiria mapear eventos,
pedidos e ingressos alheios variando o UUID.

## Armadilhas já resolvidas

Cada uma destas custou tempo. Ficam registradas para não voltarem.

**O job de expiração roda em toda instância.** Devolver estoque direto faz o
saldo voltar uma vez por réplica. O pedido é reivindicado antes por um `UPDATE`
condicional, e só quem afeta a linha devolve estoque.

**`mvn` não remove recurso deletado de `target/classes`.** Apagar uma migration
e rodar `verify` sem `clean` continua embutindo o arquivo antigo no jar. Ao mexer
em migrations, use `./mvnw clean verify`.

**`AccessDeniedException` lançada dentro do service não chega ao
`AccessDeniedHandler`.** Ela nasce depois do `DispatcherServlet`, então vira 500
se o `GlobalExceptionHandler` não a tratar.

**401 e 403 nascem fora do `GlobalExceptionHandler`.** O Spring Security responde
antes de existir controller; `ProblemDetailSecurityHandlers` garante que essas
duas respostas também sigam a RFC 7807.

**`@IdClass` não aceita `record`.** O JPA lê a chave no padrão JavaBean, e os
acessores de um record não atendem.

**`NimbusJwtEncoder` assume RS256.** Com chave HMAC é preciso declarar
`JwsHeader.with(MacAlgorithm.HS256)`, senão todo token falha com "Failed to
select a JWK signing key".

**Não teste JWT adulterado trocando o último caractere da assinatura.** Os 32
bytes do HMAC-SHA256 ocupam 43 caracteres em Base64url, e o último carrega só 4
bits úteis. Trocar esse caractere pode alterar apenas os bits ignorados, e o
token continua válido. Passou local e falhou no CI. Edite as claims.

**Ler o PNG do QR de volta exige `DecodeHintType.PURE_BARCODE`.** O detector
padrão do ZXing é feito para fotos e falha em cerca de 1,5% das imagens
sintéticas, dependendo do conteúdo.

**Boot 4 mudou nomes que o Boot 3 usava.** `flyway-core` sozinho não ativa a
autoconfiguração (use `spring-boot-starter-flyway`); os módulos do Testcontainers
viraram `testcontainers-postgresql` e `testcontainers-junit-jupiter`;
`@AutoConfigureMockMvc` mudou de pacote; e o `ObjectMapper` padrão passou a ser
o do Jackson 3.

## Interface

`src/main/resources/static`: HTML, CSS e JavaScript sem framework, servido pelo
Spring. Ao criar página nova, libere o caminho no `SecurityConfig` —
`anyRequest().authenticated()` fecha tudo por padrão, inclusive arquivo estático.

- O PNG do QR exige `Authorization`: use `fetch` + `Blob` + object URL, nunca
  `<img src>` direto
- Sistema de forma: controles 8px, containers 12px, etiquetas pílula
- Cor nova passa por cálculo de contraste antes de entrar. Mínimo WCAG AA:
  4.5:1 para texto normal, 3:1 para texto grande. Os valores atuais estão
  comentados no `style.css` com a razão medida
- Alvo de toque mínimo de 44px em qualquer controle
- A tela fala reais, a API fala centavos. A conversão usa aritmética de
  inteiros, porque `19.99 * 100` dá `1998.9999999999998` em JavaScript
- `getUserMedia` exige HTTPS ou localhost. Para testar a câmera no celular use
  um túnel; abrir pelo IP da rede não funciona

## Testes

Estado em memória não é limpo pelo banco: o `LoginAttemptService` guarda contagem
num bean singleton, e o `truncate` do `AbstractDatabaseTest` não alcança isso.
Testes que compartilham a mesma chave se contaminam, então use um e-mail por
teste.

Comportamento que depende de tempo usa um `Clock` injetado. Teste avançando o
relógio, nunca com `Thread.sleep`, que é lento e fica intermitente no CI.

## Verificando

```bash
docker compose up          # tudo
./mvnw verify              # testes, precisa do Docker
```

Os segredos ficam no `.env`, que está no `.gitignore`. Trocar o `QR_SECRET`
invalida todos os QR já emitidos: é o comportamento correto, mas em produção
significaria reemitir ingressos.
