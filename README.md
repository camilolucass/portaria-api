# portaria-api

[![CI](https://github.com/camilolucass/portaria-api/actions/workflows/ci.yml/badge.svg)](https://github.com/camilolucass/portaria-api/actions/workflows/ci.yml)

API e interface para venda de ingressos e controle de entrada por QR Code em
eventos.

Projeto de estudo. Parti de uma especificação técnica ([SPEC.md](SPEC.md)) e
implementei o sistema inteiro para praticar as partes de back-end que um CRUD
não exercita: concorrência, autenticação e integridade de dados.

## Rodando

Precisa apenas de Docker.

```bash
cp .env.example .env    # troque os três segredos por valores aleatórios
docker compose up
```

Abra http://localhost:8080. O perfil `dev` cria um evento e três contas de
demonstração, uma por papel. A senha das três é `portaria-dev-2026`.

| Conta | O que vê |
|---|---|
| `organizador@exemplo.com` | Eventos, lotes, receita e presença |
| `comprador@exemplo.com` | Compra e o QR do ingresso |
| `portaria@exemplo.com` | Leitor de QR |

A API também pode ser explorada pelo Swagger, em http://localhost:8080/docs.

## Tecnologias

Java 25 · Spring Boot 4 · Spring Security · PostgreSQL 16 · Flyway ·
JUnit 5 + Testcontainers · Docker · GitHub Actions

O front são quatro páginas em HTML, CSS e JavaScript puro, servidas pelo próprio
Spring. Sem framework de front porque o assunto do projeto é o back-end, e um
bundler aqui só somaria passo de build.

## O que o sistema faz

- O organizador cria eventos e lotes, publica, e acompanha receita e presença
- O comprador escolhe o lote, paga e recebe um QR Code
- A portaria lê o QR pela câmera e responde verde ou vermelho em tela cheia

## Os problemas interessantes

A especificação parte de quatro situações que um CRUD comum não resolve. Foram
elas que me fizeram escolher este projeto.

**1. Dois compradores levando o último ingresso.** Ler o estoque e depois somar
não funciona: entre a leitura e a escrita cabe outra requisição. A soma acontece
dentro do próprio `UPDATE`, e quem decide é o número de linhas afetadas.

```sql
UPDATE ticket_batch
   SET sold_quantity = sold_quantity + :quantity
 WHERE id = :batchId
   AND sold_quantity + :quantity <= total_quantity
```

Zero linhas afetadas significa lote esgotado, e a API responde `409`.

**2. QR Code falsificado.** O conteúdo do QR é o identificador do ingresso mais
uma assinatura HMAC-SHA256. Sem a chave, ninguém gera um código que passe na
verificação.

**3. A mesma pessoa entrando duas vezes.** Mesma ideia do estoque: o `UPDATE` só
muda o ingresso se ele ainda estiver `ISSUED`. Entre várias portarias lendo o
mesmo código ao mesmo tempo, exatamente uma entrada é liberada.

**4. O gateway de pagamento reenviando a notificação.** Gateways garantem "pelo
menos uma entrega", nunca "exatamente uma". Um `UNIQUE` na tabela de
notificações processadas faz a repetição não pagar o pedido de novo. A
confirmação em si é simulada: o webhook e a idempotência estão prontos, a
integração com um gateway real não.

## Testes

```bash
./mvnw verify
```

110 testes, todos contra um PostgreSQL de verdade que sobe em container
(Testcontainers). A especificação proíbe banco em memória mesmo em teste, e faz
sentido: os casos mais importantes são de concorrência, e o H2 não reproduz
isso.

Depois de escrever os testes, tentei quebrá-los de propósito. Removi a condição
do `UPDATE` do check-in e rodei de novo: as 20 threads passaram, ou seja, 20
pessoas entrando com o mesmo ingresso. O teste falhou, como deveria.

Fiz isso com seis proteções diferentes. Cinco falharam na hora. **Uma não**: o
teste continuava passando mesmo com a proteção removida, o que significa que ele
não testava nada. Consertei o teste, refiz a remoção, e aí sim ele quebrou. Foi
a coisa mais útil que aprendi no projeto.

## Segurança

- Autenticação por JWT, com três papéis: `ORGANIZER`, `GATE` e `BUYER`
- O organizador só enxerga os próprios eventos; a portaria só valida ingresso
  dos eventos aos quais está vinculada, e nunca vê receita
- Senha com BCrypt, e login com limite de tentativas
- E-mail inexistente e senha errada devolvem a mesma resposta, para não revelar
  quais e-mails têm conta
- Erros seguem o padrão RFC 7807 (`application/problem+json`)

## Organização

```
src/main/java/br/com/portaria/
├── event/       eventos
├── batch/       lotes de ingressos
├── order/       pedidos e expiração
├── ticket/      ingressos, assinatura e QR
├── checkin/     validação de entrada
├── payment/     webhook de pagamento
├── identity/    autenticação e autorização
├── stats/       painel do organizador
├── demo/        dados de demonstração do perfil dev
└── shared/      exceções e configuração
```

Um pacote por assunto, e não por camada. As migrations do banco ficam em
`src/main/resources/db/migration`, e o front em `src/main/resources/static`.

## Notas

O projeto roda em Java 25 com Spring Boot 4, enquanto a especificação pede
Java 21 com Boot 3.3. Foi decisão de ambiente. Essas diferenças, e as outras
decisões que tomei fora do que a especificação dizia, estão registradas em
[DECISOES.md](DECISOES.md).
