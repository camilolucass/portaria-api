package br.com.portaria.stats;

import br.com.portaria.event.Event;
import br.com.portaria.event.EventService;
import br.com.portaria.order.OrderStatus;
import br.com.portaria.ticket.TicketStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class StatsService {

    private final StatsRepository repository;
    private final EventService eventService;

    public StatsService(StatsRepository repository, EventService eventService) {
        this.repository = repository;
        this.eventService = eventService;
    }

    /**
     * Os totais do evento sao a soma das linhas por lote, nao uma terceira
     * consulta: assim o cabecalho e o detalhamento nunca divergem, mesmo que
     * alguem compre um ingresso entre uma query e outra.
     */
    @Transactional(readOnly = true)
    public EventStatsResponse of(UUID eventPublicId) {
        // dado financeiro: so o dono do evento. GATE nunca chega aqui (PreAuthorize)
        Event event = eventService.findOwned(eventPublicId);

        List<BatchStatsProjection> byBatch = repository.statsByBatch(
                event.getId(), TicketStatus.USED, TicketStatus.CANCELLED);

        long totalIssued = byBatch.stream().mapToLong(BatchStatsProjection::issued).sum();
        long totalCheckedIn = byBatch.stream().mapToLong(BatchStatsProjection::checkedIn).sum();
        long totalRevenueCents = repository.revenueCents(event.getId(), OrderStatus.PAID);

        return new EventStatsResponse(
                totalIssued,
                totalCheckedIn,
                totalRevenueCents,
                byBatch.stream().map(BatchStatsResponse::from).toList());
    }
}
