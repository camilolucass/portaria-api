package br.com.portaria.ticket;

import br.com.portaria.AbstractDatabaseTest;
import br.com.portaria.batch.TicketBatch;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.event.Event;
import br.com.portaria.event.EventRepository;
import br.com.portaria.event.EventStatus;
import br.com.portaria.order.Buyer;
import br.com.portaria.order.BuyerRepository;
import br.com.portaria.order.OrderRepository;
import br.com.portaria.order.OrderStatus;
import br.com.portaria.order.PurchaseOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketControllerTest extends AbstractDatabaseTest {

    @Autowired
    private QrCodeSigner signer;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketBatchRepository batchRepository;

    @Autowired
    private BuyerRepository buyerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void deveDevolverOIngressoComOCodigoAssinado() throws Exception {
        Ticket ticket = issuedTicket();

        mockMvc.perform(get("/api/v1/tickets/{id}", ticket.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticket.getPublicId().toString()))
                .andExpect(jsonPath("$.holderName").value("Ana Souza"))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.eventName").value("Festa Universitaria 2026"))
                .andExpect(jsonPath("$.code").value(signer.sign(ticket.getPublicId())));
    }

    @Test
    void deveDevolverOQrComoPng() throws Exception {
        Ticket ticket = issuedTicket();

        mockMvc.perform(get("/api/v1/tickets/{id}/qr", ticket.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void deveDevolver404ParaIngressoInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Ingresso nao encontrado"));
    }

    private Ticket issuedTicket() {
        LocalDateTime now = LocalDateTime.now();

        Event event = eventRepository.save(Event.builder()
                .name("Festa Universitaria 2026")
                .venue("Centro de Eventos, Orleans/SC")
                .gateOpensAt(now.plusDays(1))
                .startsAt(now.plusDays(1).plusHours(1))
                .endsAt(now.plusDays(1).plusHours(7))
                .status(EventStatus.PUBLISHED)
                .build());

        TicketBatch batch = batchRepository.save(TicketBatch.builder()
                .event(event)
                .name("1o lote")
                .priceCents(4500)
                .totalQuantity(10)
                .soldQuantity(1)
                .salesStart(now.minusDays(10))
                .salesEnd(now.plusHours(1))
                .build());

        Buyer buyer = buyerRepository.save(Buyer.builder()
                .name("Lucas Camilo")
                .email("lucas@exemplo.com")
                .document("12345678900")
                .build());

        PurchaseOrder order = orderRepository.save(PurchaseOrder.builder()
                .buyer(buyer)
                .batch(batch)
                .quantity(1)
                .totalCents(4500)
                .status(OrderStatus.PAID)
                .expiresAt(now.plusMinutes(15))
                .paidAt(now)
                .build());

        return ticketRepository.save(Ticket.builder()
                .order(order)
                .batch(batch)
                .holderName("Ana Souza")
                .holderDocument("98765432100")
                .status(TicketStatus.ISSUED)
                .build());
    }
}
