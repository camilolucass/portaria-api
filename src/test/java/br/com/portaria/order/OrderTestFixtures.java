package br.com.portaria.order;

import br.com.portaria.batch.TicketBatch;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.event.Event;
import br.com.portaria.event.EventRepository;
import br.com.portaria.event.EventStatus;
import br.com.portaria.identity.AppUser;

import java.time.LocalDateTime;

/** Evento publicado com um lote em janela de venda aberta agora. */
final class OrderTestFixtures {

    private OrderTestFixtures() {
    }

    static TicketBatch publishedBatchWithSlots(EventRepository eventRepository,
                                               TicketBatchRepository batchRepository,
                                               AppUser organizer,
                                               int totalQuantity,
                                               int priceCents) {
        LocalDateTime now = LocalDateTime.now();

        Event event = eventRepository.save(Event.builder()
                .organizer(organizer)
                .name("Festa Universitaria 2026")
                .venue("Centro de Eventos, Orleans/SC")
                .gateOpensAt(now.plusDays(2).minusHours(1))
                .startsAt(now.plusDays(2))
                .endsAt(now.plusDays(3))
                .status(EventStatus.PUBLISHED)
                .build());

        return batchRepository.save(TicketBatch.builder()
                .event(event)
                .name("1o lote")
                .priceCents(priceCents)
                .totalQuantity(totalQuantity)
                .soldQuantity(0)
                .salesStart(now.minusDays(1))
                .salesEnd(now.plusDays(1))
                .build());
    }

    static CreateOrderRequest orderFor(TicketBatch batch, int quantity, String document) {
        var buyer = new BuyerRequest("Lucas Camilo", "lucas@exemplo.com", document);
        var holders = new java.util.ArrayList<HolderRequest>();
        for (int i = 0; i < quantity; i++) {
            holders.add(new HolderRequest("Titular " + i, null));
        }
        return new CreateOrderRequest(batch.getPublicId(), quantity, buyer, holders);
    }
}
