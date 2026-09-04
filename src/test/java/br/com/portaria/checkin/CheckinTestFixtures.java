package br.com.portaria.checkin;

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
import br.com.portaria.ticket.Ticket;
import br.com.portaria.ticket.TicketRepository;
import br.com.portaria.ticket.TicketStatus;

import java.time.LocalDateTime;

/**
 * Monta o cenario de portaria direto pelos repositories: o check-in nao depende
 * do fluxo de compra, e amarrar os dois deixaria estes testes fragis.
 */
final class CheckinTestFixtures {

    private CheckinTestFixtures() {
    }

    /** Evento com os portoes abertos agora. */
    static Ticket issuedTicketWithOpenGate(EventRepository events,
                                           TicketBatchRepository batches,
                                           BuyerRepository buyers,
                                           OrderRepository orders,
                                           TicketRepository tickets,
                                           String document) {
        LocalDateTime now = LocalDateTime.now();
        return ticket(events, batches, buyers, orders, tickets, document,
                now.minusHours(2), now.minusHours(1), now.plusHours(5), TicketStatus.ISSUED);
    }

    /** Evento cujos portoes so abrem amanha (TC-05). */
    static Ticket issuedTicketBeforeGateOpens(EventRepository events,
                                              TicketBatchRepository batches,
                                              BuyerRepository buyers,
                                              OrderRepository orders,
                                              TicketRepository tickets,
                                              String document) {
        LocalDateTime now = LocalDateTime.now();
        return ticket(events, batches, buyers, orders, tickets, document,
                now.plusDays(1), now.plusDays(1).plusHours(1), now.plusDays(1).plusHours(7),
                TicketStatus.ISSUED);
    }

    /** Ingresso cancelado, portoes abertos (TC-06). */
    static Ticket cancelledTicket(EventRepository events,
                                  TicketBatchRepository batches,
                                  BuyerRepository buyers,
                                  OrderRepository orders,
                                  TicketRepository tickets,
                                  String document) {
        LocalDateTime now = LocalDateTime.now();
        return ticket(events, batches, buyers, orders, tickets, document,
                now.minusHours(2), now.minusHours(1), now.plusHours(5), TicketStatus.CANCELLED);
    }

    private static Ticket ticket(EventRepository events,
                                 TicketBatchRepository batches,
                                 BuyerRepository buyers,
                                 OrderRepository orders,
                                 TicketRepository tickets,
                                 String document,
                                 LocalDateTime gateOpensAt,
                                 LocalDateTime startsAt,
                                 LocalDateTime endsAt,
                                 TicketStatus status) {

        Event event = events.save(Event.builder()
                .name("Festa Universitaria 2026")
                .venue("Centro de Eventos, Orleans/SC")
                .gateOpensAt(gateOpensAt)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .status(EventStatus.PUBLISHED)
                .build());

        TicketBatch batch = batches.save(TicketBatch.builder()
                .event(event)
                .name("1o lote")
                .priceCents(4500)
                .totalQuantity(10)
                .soldQuantity(1)
                .salesStart(gateOpensAt.minusDays(30))
                .salesEnd(gateOpensAt.minusDays(1))
                .build());

        Buyer buyer = buyers.save(Buyer.builder()
                .name("Lucas Camilo")
                .email("lucas@exemplo.com")
                .document(document)
                .build());

        PurchaseOrder order = orders.save(PurchaseOrder.builder()
                .buyer(buyer)
                .batch(batch)
                .quantity(1)
                .totalCents(4500)
                .status(OrderStatus.PAID)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .paidAt(LocalDateTime.now())
                .build());

        return tickets.save(Ticket.builder()
                .order(order)
                .batch(batch)
                .holderName("Ana Souza")
                .holderDocument("98765432100")
                .status(status)
                .build());
    }
}
