package br.com.portaria.identity;

import br.com.portaria.AbstractDatabaseTest;
import br.com.portaria.batch.CreateBatchRequest;
import br.com.portaria.batch.TicketBatch;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.checkin.CheckinRequest;
import br.com.portaria.event.CreateEventRequest;
import br.com.portaria.event.Event;
import br.com.portaria.event.EventRepository;
import br.com.portaria.event.EventStaff;
import br.com.portaria.event.EventStaffRepository;
import br.com.portaria.event.EventStatus;
import br.com.portaria.order.BuyerRequest;
import br.com.portaria.order.CreateOrderRequest;
import br.com.portaria.order.HolderRequest;
import br.com.portaria.order.OrderResponse;
import br.com.portaria.order.OrderService;
import br.com.portaria.ticket.QrCodeSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase 2, Etapa 2 — a matriz de negacao.
 *
 * Cada teste aqui existe porque, sem ele, uma permissao a mais passaria
 * despercebida: o codigo continua funcionando quando permite demais. Estes sao
 * os testes que falham quando alguem afrouxa uma regra.
 *
 * Convencao de status: falta de papel devolve **403**; recurso que existe mas e
 * de outra conta devolve **404**. Um 403 no segundo caso confirmaria a
 * existencia daquele identificador, permitindo mapear eventos, pedidos e
 * ingressos alheios so variando o UUID.
 */
class AuthorizationTest extends AbstractDatabaseTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketBatchRepository batchRepository;

    @Autowired
    private EventStaffRepository eventStaffRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private QrCodeSigner signer;

    private AppUser organizer;
    private AppUser otherOrganizer;
    private AppUser buyer;
    private AppUser otherBuyer;
    private AppUser gate;
    private AppUser otherGate;

    @BeforeEach
    void createAccounts() {
        organizer = createUser("organizadora@exemplo.com", Role.ORGANIZER);
        otherOrganizer = createUser("outro.organizador@exemplo.com", Role.ORGANIZER);
        buyer = createUser("compradora@exemplo.com", Role.BUYER);
        otherBuyer = createUser("outro.comprador@exemplo.com", Role.BUYER);
        gate = createUser("portaria@exemplo.com", Role.GATE);
        otherGate = createUser("outra.portaria@exemplo.com", Role.GATE);
    }

    private Event event(AppUser owner, EventStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return eventRepository.save(Event.builder()
                .organizer(owner)
                .name("Festa de " + owner.getEmail())
                .venue("Centro de Eventos")
                .gateOpensAt(now.minusHours(2))
                .startsAt(now.minusHours(1))
                .endsAt(now.plusHours(6))
                .status(status)
                .build());
    }

    private TicketBatch batch(Event event) {
        LocalDateTime now = LocalDateTime.now();
        return batchRepository.save(TicketBatch.builder()
                .event(event)
                .name("1o lote")
                .priceCents(4500)
                .totalQuantity(50)
                .soldQuantity(0)
                .salesStart(now.minusDays(10))
                .salesEnd(now.plusDays(1))
                .build());
    }

    private OrderResponse paidOrder(AppUser account, TicketBatch batch, String document) {
        authenticateAs(account);
        var order = orderService.create(new CreateOrderRequest(
                batch.getPublicId(), 1,
                new BuyerRequest("Comprador", "c@exemplo.com", document),
                List.of(new HolderRequest("Titular", null))));
        return orderService.pay(order.id());
    }

    // ---------------------------------------------------------- papel exigido

    @Test
    void compradorNaoPodeCriarEvento() throws Exception {
        performAs(buyer, post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateEventRequest("Festa", null, "Local",
                                LocalDateTime.now().plusDays(1),
                                LocalDateTime.now().plusDays(1).plusHours(1),
                                LocalDateTime.now().plusDays(1).plusHours(6)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    void portariaNaoPodeCriarEvento() throws Exception {
        performAs(gate, post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateEventRequest("Festa", null, "Local",
                                LocalDateTime.now().plusDays(1),
                                LocalDateTime.now().plusDays(1).plusHours(1),
                                LocalDateTime.now().plusDays(1).plusHours(6)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizadorNaoPodeComprar() throws Exception {
        TicketBatch batch = batch(event(organizer, EventStatus.PUBLISHED));

        performAs(organizer, post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateOrderRequest(batch.getPublicId(), 1,
                                new BuyerRequest("X", "x@exemplo.com", "12345678900"),
                                List.of(new HolderRequest("Titular", null))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizadorNaoPodeFazerCheckin() throws Exception {
        Event owned = event(organizer, EventStatus.PUBLISHED);
        var ticket = paidOrder(buyer, batch(owned), "12345678900").tickets().get(0);

        performAs(organizer, post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckinRequest(signer.sign(ticket.id())))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------- dado financeiro (SPEC)

    /** O SPEC e explicito: operador de portaria nunca ve dado financeiro. */
    @Test
    void portariaNaoPodeVerEstatisticas() throws Exception {
        Event owned = event(organizer, EventStatus.PUBLISHED);
        eventStaffRepository.save(new EventStaff(owned, gate));

        performAs(gate, get("/api/v1/events/{id}/stats", owned.getPublicId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void compradorNaoPodeVerEstatisticas() throws Exception {
        Event owned = event(organizer, EventStatus.PUBLISHED);

        performAs(buyer, get("/api/v1/events/{id}/stats", owned.getPublicId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizadorNaoVeEstatisticasDeEventoAlheio() throws Exception {
        Event alheio = event(otherOrganizer, EventStatus.PUBLISHED);

        performAs(organizer, get("/api/v1/events/{id}/stats", alheio.getPublicId()))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------- dono do evento

    @Test
    void organizadorNaoPublicaEventoAlheio() throws Exception {
        Event alheio = event(otherOrganizer, EventStatus.DRAFT);

        performAs(organizer, post("/api/v1/events/{id}/publish", alheio.getPublicId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void organizadorNaoCriaLoteEmEventoAlheio() throws Exception {
        Event alheio = event(otherOrganizer, EventStatus.PUBLISHED);
        var request = new CreateBatchRequest("1o lote", 4500, 10,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().minusMinutes(30));

        performAs(organizer, post("/api/v1/events/{id}/batches", alheio.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rascunhoDeOutroOrganizadorNaoAparece() throws Exception {
        Event rascunhoAlheio = event(otherOrganizer, EventStatus.DRAFT);

        performAs(organizer, get("/api/v1/events/{id}", rascunhoAlheio.getPublicId()))
                .andExpect(status().isNotFound());
        performAs(buyer, get("/api/v1/events/{id}", rascunhoAlheio.getPublicId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listagemDoOrganizadorTrazApenasOsProprios() throws Exception {
        event(organizer, EventStatus.DRAFT);
        event(otherOrganizer, EventStatus.PUBLISHED);
        event(otherOrganizer, EventStatus.PUBLISHED);

        performAs(organizer, get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    /** Quem compra precisa navegar: ve os publicados de qualquer organizador. */
    @Test
    void listagemDoCompradorTrazApenasOsPublicados() throws Exception {
        event(organizer, EventStatus.DRAFT);
        event(organizer, EventStatus.PUBLISHED);
        event(otherOrganizer, EventStatus.PUBLISHED);

        performAs(buyer, get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    // -------------------------------------------------- portaria vinculada

    @Test
    void portariaNaoValidaIngressoDeEventoAoQualNaoEstaVinculada() throws Exception {
        Event evento = event(organizer, EventStatus.PUBLISHED);
        eventStaffRepository.save(new EventStaff(evento, gate));
        var ticket = paidOrder(buyer, batch(evento), "12345678900").tickets().get(0);

        // otherGate opera a portaria de outro evento qualquer
        performAs(otherGate, post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckinRequest(signer.sign(ticket.id())))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Portaria nao vinculada ao evento"));
    }

    @Test
    void portariaVinculadaValidaNormalmente() throws Exception {
        Event evento = event(organizer, EventStatus.PUBLISHED);
        eventStaffRepository.save(new EventStaff(evento, gate));
        var ticket = paidOrder(buyer, batch(evento), "12345678900").tickets().get(0);

        performAs(gate, post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckinRequest(signer.sign(ticket.id())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("GRANTED"));
    }

    // ------------------------------------------------------- dono do pedido

    @Test
    void compradorNaoVePedidoDeOutroComprador() throws Exception {
        Event evento = event(organizer, EventStatus.PUBLISHED);
        var order = paidOrder(buyer, batch(evento), "12345678900");

        performAs(otherBuyer, get("/api/v1/orders/{id}", order.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Pedido nao encontrado"));
    }

    @Test
    void compradorNaoCancelaPedidoDeOutroComprador() throws Exception {
        Event evento = event(organizer, EventStatus.PUBLISHED);
        var order = paidOrder(buyer, batch(evento), "12345678900");

        performAs(otherBuyer, post("/api/v1/orders/{id}/cancel", order.id()))
                .andExpect(status().isNotFound());
    }

    /**
     * O caso mais grave da Fase 1: quem descobrisse o publicId de um ingresso
     * lia o codigo do QR e entrava na festa no lugar do dono.
     */
    @Test
    void compradorNaoLeOQrDeIngressoAlheio() throws Exception {
        Event evento = event(organizer, EventStatus.PUBLISHED);
        var ticket = paidOrder(buyer, batch(evento), "12345678900").tickets().get(0);

        performAs(otherBuyer, get("/api/v1/tickets/{id}", ticket.id()))
                .andExpect(status().isNotFound());
        performAs(otherBuyer, get("/api/v1/tickets/{id}/qr", ticket.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void donoLeOProprioIngressoNormalmente() throws Exception {
        Event evento = event(organizer, EventStatus.PUBLISHED);
        var ticket = paidOrder(buyer, batch(evento), "12345678900").tickets().get(0);

        performAs(buyer, get("/api/v1/tickets/{id}", ticket.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNotEmpty());
    }
}
