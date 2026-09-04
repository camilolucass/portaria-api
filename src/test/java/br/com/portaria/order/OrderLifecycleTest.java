package br.com.portaria.order;

import br.com.portaria.AbstractDatabaseTest;
import br.com.portaria.batch.TicketBatch;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.event.EventRepository;
import br.com.portaria.ticket.TicketRepository;
import br.com.portaria.ticket.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TC-07, TC-08, TC-09 e os contratos de erro de POST /orders. */
class OrderLifecycleTest extends AbstractDatabaseTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderExpirationJob expirationJob;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketBatchRepository batchRepository;

    private TicketBatch batch(int slots) {
        return OrderTestFixtures.publishedBatchWithSlots(eventRepository, batchRepository, slots, 4500);
    }

    // RN-04 e RN-05 -----------------------------------------------------------

    @Test
    void deveCriarPedidoPendenteReservandoEstoque() throws Exception {
        TicketBatch batch = batch(10);
        var request = OrderTestFixtures.orderFor(batch, 2, "12345678900");

        perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.totalCents").value(9000))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.tickets").isEmpty());

        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getSoldQuantity())
                .isEqualTo(2);
    }

    @Test
    void deveRecusarPedidoMaiorQueOEstoqueCom409() throws Exception {
        TicketBatch batch = batch(1);
        var request = OrderTestFixtures.orderFor(batch, 2, "12345678900");

        perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Lote esgotado"));

        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getSoldQuantity())
                .isZero();
    }

    // TC-09 -------------------------------------------------------------------

    @Test
    void deveRecusarQuantidadeDiferenteDoNumeroDeTitularesCom400() throws Exception {
        TicketBatch batch = batch(10);
        var buyer = new BuyerRequest("Lucas", "lucas@exemplo.com", "12345678900");
        var holders = List.of(
                new HolderRequest("Titular 1", null),
                new HolderRequest("Titular 2", null),
                new HolderRequest("Titular 3", null));
        var request = new CreateOrderRequest(batch.getPublicId(), 2, buyer, holders);

        perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisicao invalida"))
                .andExpect(jsonPath("$.fields.holdersMatchingQuantity").isNotEmpty());

        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getSoldQuantity())
                .isZero();
    }

    // TC-08 e RN-07 -----------------------------------------------------------

    @Test
    void devePagarGerandoUmIngressoPorTitular() throws Exception {
        TicketBatch batch = batch(10);
        var created = orderService.create(OrderTestFixtures.orderFor(batch, 3, "12345678900"));

        perform(post("/api/v1/orders/{id}/pay", created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty())
                .andExpect(jsonPath("$.tickets.length()").value(3))
                .andExpect(jsonPath("$.tickets[0].holderName").value("Titular 0"))
                .andExpect(jsonPath("$.tickets[2].holderName").value("Titular 2"))
                .andExpect(jsonPath("$.tickets[0].status").value("ISSUED"));

        var order = orderRepository.findByPublicId(created.id()).orElseThrow();
        assertThat(ticketRepository.findByOrderIdOrderByIdAsc(order.getId())).hasSize(3);
    }

    @Test
    void naoDeveGerarIngressoAntesDoPagamento() {
        TicketBatch batch = batch(10);
        var created = orderService.create(OrderTestFixtures.orderFor(batch, 2, "12345678900"));

        var order = orderRepository.findByPublicId(created.id()).orElseThrow();
        assertThat(ticketRepository.findByOrderIdOrderByIdAsc(order.getId())).isEmpty();
    }

    @Test
    void deveRecusarPagamentoRepetidoCom409() throws Exception {
        TicketBatch batch = batch(10);
        var created = orderService.create(OrderTestFixtures.orderFor(batch, 1, "12345678900"));
        orderService.pay(created.id());

        perform(post("/api/v1/orders/{id}/pay", created.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Situacao do pedido nao permite a operacao"));

        var order = orderRepository.findByPublicId(created.id()).orElseThrow();
        assertThat(ticketRepository.findByOrderIdOrderByIdAsc(order.getId())).hasSize(1);
    }

    @Test
    void deveDevolver404ParaPedidoInexistente() throws Exception {
        perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Pedido nao encontrado"));
    }

    @Test
    void deveDevolverOsIngressosNaConsultaDoPedido() throws Exception {
        TicketBatch batch = batch(10);
        var created = orderService.create(OrderTestFixtures.orderFor(batch, 2, "12345678900"));
        orderService.pay(created.id());

        perform(get("/api/v1/orders/{id}", created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.batchName").value("1o lote"))
                .andExpect(jsonPath("$.eventName").value("Festa Universitaria 2026"))
                .andExpect(jsonPath("$.buyer.document").value("12345678900"))
                .andExpect(jsonPath("$.tickets.length()").value(2));
    }

    // TC-07 e RN-06 -----------------------------------------------------------

    @Test
    void deveExpirarPedidoVencidoEDevolverOEstoque() {
        TicketBatch batch = batch(10);
        var created = orderService.create(OrderTestFixtures.orderFor(batch, 4, "12345678900"));
        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getSoldQuantity())
                .isEqualTo(4);

        var order = orderRepository.findByPublicId(created.id()).orElseThrow();
        order.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        orderRepository.save(order);

        expirationJob.expirePendingOrders();

        assertThat(orderRepository.findByPublicId(created.id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.EXPIRED);
        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getSoldQuantity())
                .isZero();
    }

    @Test
    void naoDeveExpirarPedidoDentroDoPrazo() {
        TicketBatch batch = batch(10);
        var created = orderService.create(OrderTestFixtures.orderFor(batch, 2, "12345678900"));

        expirationJob.expirePendingOrders();

        assertThat(orderRepository.findByPublicId(created.id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getSoldQuantity())
                .isEqualTo(2);
    }

    // RN-08 -------------------------------------------------------------------

    @Test
    void deveCancelarPedidoPagoCancelandoIngressosEDevolvendoEstoque() throws Exception {
        TicketBatch batch = batch(10);
        var created = orderService.create(OrderTestFixtures.orderFor(batch, 2, "12345678900"));
        orderService.pay(created.id());

        perform(post("/api/v1/orders/{id}/cancel", created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        var order = orderRepository.findByPublicId(created.id()).orElseThrow();
        assertThat(ticketRepository.findByOrderIdOrderByIdAsc(order.getId()))
                .allMatch(ticket -> ticket.getStatus() == TicketStatus.CANCELLED);
        assertThat(batchRepository.findById(batch.getId()).orElseThrow().getSoldQuantity())
                .isZero();
    }
}
