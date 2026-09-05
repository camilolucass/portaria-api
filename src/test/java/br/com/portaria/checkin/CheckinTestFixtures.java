package br.com.portaria.checkin;

import br.com.portaria.batch.TicketBatch;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.event.Event;
import br.com.portaria.event.EventRepository;
import br.com.portaria.event.EventStaff;
import br.com.portaria.event.EventStaffRepository;
import br.com.portaria.event.EventStatus;
import br.com.portaria.identity.AppUser;
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

    /** Tudo que um check-in precisa: evento, lote, pedido pago e o ingresso. */
    record Scenario(AppUser organizer, AppUser gate, AppUser buyer, Ticket ticket) {
    }

    /** Evento com os portoes abertos agora. */
    static Scenario issuedTicketWithOpenGate(EventRepository events,
                                           TicketBatchRepository batches,
                                           BuyerRepository buyers,
                                           OrderRepository orders,
                                           TicketRepository tickets,
                                           EventStaffRepository staff,
                                           br.com.portaria.identity.AppUserRepository users,
                                           String document) {
        LocalDateTime now = LocalDateTime.now();
        return scenario(events, batches, buyers, orders, tickets, staff, users, document,
                now.minusHours(2), now.minusHours(1), now.plusHours(5), TicketStatus.ISSUED);
    }

    /** Evento cujos portoes so abrem amanha (TC-05). */
    static Scenario issuedTicketBeforeGateOpens(EventRepository events,
                                              TicketBatchRepository batches,
                                              BuyerRepository buyers,
                                              OrderRepository orders,
                                              TicketRepository tickets,
                                              EventStaffRepository staff,
                                              br.com.portaria.identity.AppUserRepository users,
                                              String document) {
        LocalDateTime now = LocalDateTime.now();
        return scenario(events, batches, buyers, orders, tickets, staff, users, document,
                now.plusDays(1), now.plusDays(1).plusHours(1), now.plusDays(1).plusHours(7),
                TicketStatus.ISSUED);
    }

    /** Ingresso cancelado, portoes abertos (TC-06). */
    static Scenario cancelledTicket(EventRepository events,
                                  TicketBatchRepository batches,
                                  BuyerRepository buyers,
                                  OrderRepository orders,
                                  TicketRepository tickets,
                                  EventStaffRepository staff,
                                  br.com.portaria.identity.AppUserRepository users,
                                  String document) {
        LocalDateTime now = LocalDateTime.now();
        return scenario(events, batches, buyers, orders, tickets, staff, users, document,
                now.minusHours(2), now.minusHours(1), now.plusHours(5), TicketStatus.CANCELLED);
    }

    private static Scenario scenario(EventRepository events,
                                 TicketBatchRepository batches,
                                 BuyerRepository buyers,
                                 OrderRepository orders,
                                 TicketRepository tickets,
                                 EventStaffRepository staff,
                                 br.com.portaria.identity.AppUserRepository users,
                                 String document,
                                 LocalDateTime gateOpensAt,
                                 LocalDateTime startsAt,
                                 LocalDateTime endsAt,
                                 TicketStatus status) {

        AppUser organizer = users.save(AppUser.builder()
                .name("Organizadora").email("organizadora@exemplo.com")
                .passwordHash("{noop}x")
                .roles(java.util.EnumSet.of(br.com.portaria.identity.Role.ORGANIZER)).build());
        AppUser gate = users.save(AppUser.builder()
                .name("Portaria").email("portaria@exemplo.com")
                .passwordHash("{noop}x")
                .roles(java.util.EnumSet.of(br.com.portaria.identity.Role.GATE)).build());
        AppUser account = users.save(AppUser.builder()
                .name("Compradora").email("compradora@exemplo.com")
                .passwordHash("{noop}x")
                .roles(java.util.EnumSet.of(br.com.portaria.identity.Role.BUYER)).build());

        Event event = events.save(Event.builder()
                .organizer(organizer)
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

        staff.save(new EventStaff(event, gate));

        PurchaseOrder order = orders.save(PurchaseOrder.builder()
                .user(account)
                .buyer(buyer)
                .batch(batch)
                .quantity(1)
                .totalCents(4500)
                .status(OrderStatus.PAID)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .paidAt(LocalDateTime.now())
                .build());

        Ticket ticket = tickets.save(Ticket.builder()
                .order(order)
                .batch(batch)
                .holderName("Ana Souza")
                .holderDocument("98765432100")
                .status(status)
                .build());

        return new Scenario(organizer, gate, account, ticket);
    }
}
