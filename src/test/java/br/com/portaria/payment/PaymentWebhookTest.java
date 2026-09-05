package br.com.portaria.payment;

import br.com.portaria.AbstractDatabaseTest;
import br.com.portaria.batch.TicketBatch;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.event.Event;
import br.com.portaria.event.EventRepository;
import br.com.portaria.event.EventStatus;
import br.com.portaria.identity.AppUser;
import br.com.portaria.identity.Role;
import br.com.portaria.order.BuyerRequest;
import br.com.portaria.order.CreateOrderRequest;
import br.com.portaria.order.HolderRequest;
import br.com.portaria.order.OrderRepository;
import br.com.portaria.order.OrderService;
import br.com.portaria.order.OrderStatus;
import br.com.portaria.order.PurchaseOrder;
import br.com.portaria.ticket.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Problema P4 — o gateway reenvia a mesma notificacao.
 *
 * Nao e falha do gateway: a garantia que eles dao e "pelo menos uma entrega".
 * Timeout da nossa ponta, deploy no meio do processamento, retry programado — a
 * mesma notificacao volta. Sem defesa, o pedido e pago varias vezes e os
 * ingressos sao emitidos varias vezes.
 */
class PaymentWebhookTest extends AbstractDatabaseTest {

    private static final int THREADS = 20;
    private static final int QUANTITY = 2;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private PaymentWebhookService webhookService;

    @Autowired
    private PaymentEventRepository eventRepository;

    @Autowired
    private EventRepository eventRepository2;

    @Autowired
    private TicketBatchRepository batchRepository;

    private AppUser buyer;

    @BeforeEach
    void createAccounts() {
        AppUser organizer = createUser("organizadora@exemplo.com", Role.ORGANIZER);
        buyer = createUser("compradora@exemplo.com", Role.BUYER);
        authenticateAs(buyer);

        LocalDateTime now = LocalDateTime.now();
        Event event = eventRepository2.save(Event.builder()
                .organizer(organizer)
                .name("Festa Universitaria 2026")
                .venue("Centro de Eventos")
                .gateOpensAt(now.plusDays(30))
                .startsAt(now.plusDays(30).plusHours(1))
                .endsAt(now.plusDays(30).plusHours(7))
                .status(EventStatus.PUBLISHED)
                .build());

        batchRepository.save(TicketBatch.builder()
                .event(event)
                .name("1o lote")
                .priceCents(4500)
                .totalQuantity(100)
                .soldQuantity(0)
                .salesStart(now.minusDays(1))
                .salesEnd(now.plusDays(20))
                .build());
    }

    private PurchaseOrder pendingOrder() {
        TicketBatch batch = batchRepository.findAll().get(0);
        var created = orderService.create(new CreateOrderRequest(
                batch.getPublicId(), QUANTITY,
                new BuyerRequest("Lucas", "lucas@exemplo.com", "12345678900"),
                List.of(new HolderRequest("Titular 1", null), new HolderRequest("Titular 2", null))));
        return orderRepository.findByPublicId(created.id()).orElseThrow();
    }

    private int ticketCount(PurchaseOrder order) {
        return ticketRepository.findByOrderIdOrderByIdAsc(order.getId()).size();
    }

    // ----------------------------------------------------------------- P4

    /**
     * O teste que prova o P4: a mesma notificacao entregue por 20 threads ao
     * mesmo tempo. Exatamente uma processa, e os ingressos sao emitidos uma vez
     * so — nao 20 vezes.
     */
    @Test
    void aMesmaNotificacaoChegandoVinteVezesPagaOPedidoUmaVezSo() throws Exception {
        PurchaseOrder order = pendingOrder();
        String externalId = order.getPaymentReference().toString();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    var result = webhookService.process(externalId);
                    if (result == PaymentWebhookService.WebhookResult.PROCESSED) {
                        processed.incrementAndGet();
                    } else if (result == PaymentWebhookService.WebhookResult.DUPLICATE) {
                        duplicates.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ignored) {
                    // perder a corrida e esperado; o que importa e o estado final
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(processed.get())
                .as("mais de uma notificacao foi processada")
                .isEqualTo(1);

        PurchaseOrder reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);

        // o que doeria de verdade: 20 notificacoes emitindo 40 ingressos
        assertThat(ticketCount(reloaded))
                .as("ingressos emitidos mais de uma vez")
                .isEqualTo(QUANTITY);

        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(duplicates.get()).isGreaterThan(0);
    }

    @Test
    void notificacaoRepetidaEmSequenciaNaoRefazNada() {
        PurchaseOrder order = pendingOrder();
        String externalId = order.getPaymentReference().toString();

        assertThat(webhookService.process(externalId))
                .isEqualTo(PaymentWebhookService.WebhookResult.PROCESSED);

        for (int i = 0; i < 5; i++) {
            assertThat(webhookService.process(externalId))
                    .isEqualTo(PaymentWebhookService.WebhookResult.DUPLICATE);
        }

        assertThat(ticketCount(order)).isEqualTo(QUANTITY);
    }

    @Test
    void notificacaoDePagamentoDesconhecidoNaoQuebra() {
        assertThat(webhookService.process(UUID.randomUUID().toString()))
                .isEqualTo(PaymentWebhookService.WebhookResult.IGNORED);
        assertThat(webhookService.process("nem-parece-um-identificador"))
                .isEqualTo(PaymentWebhookService.WebhookResult.IGNORED);
        assertThat(eventRepository.count()).isZero();
    }

    // ------------------------------------------------------------ a rota

    private String body(String externalId) throws Exception {
        return json(new PaymentNotification(externalId));
    }

    @Test
    void aRotaDoWebhookExigeOSegredoCompartilhado() throws Exception {
        PurchaseOrder order = pendingOrder();

        // sem o cabecalho: qualquer um marcaria pedidos como pagos com um curl
        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(order.getPaymentReference().toString())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Webhook nao autenticado"));

        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .header("X-Webhook-Secret", "segredo-errado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(order.getPaymentReference().toString())))
                .andExpect(status().isUnauthorized());

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(ticketCount(order)).isZero();
    }

    @Test
    void comOSegredoCorretoARotaProcessaEDepoisIgnoraARepeticao() throws Exception {
        PurchaseOrder order = pendingOrder();
        String payload = body(order.getPaymentReference().toString());

        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .header("X-Webhook-Secret", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PROCESSED"));

        // a repeticao responde 200: erro faria o gateway reenviar em backoff por
        // horas por algo que ja esta resolvido
        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .header("X-Webhook-Secret", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DUPLICATE"));

        assertThat(ticketCount(order)).isEqualTo(QUANTITY);
    }

    /** O corpo nao carrega situacao: nao ha campo para um atacante preencher. */
    @Test
    void oCorpoNaoAceitaSituacaoDePagamento() throws Exception {
        PurchaseOrder order = pendingOrder();

        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .header("X-Webhook-Secret", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"" + order.getPaymentReference()
                                + "\",\"status\":\"APPROVED\",\"amount\":1}"))
                .andExpect(status().isOk());

        // o campo extra foi ignorado; quem decidiu foi a consulta ao gateway
        assertThat(eventRepository.findAll().get(0).getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }
}
