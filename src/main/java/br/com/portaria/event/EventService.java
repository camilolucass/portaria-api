package br.com.portaria.event;

import br.com.portaria.shared.exception.EventNotFoundException;
import br.com.portaria.shared.exception.InvalidEventStatusException;
import br.com.portaria.shared.exception.InvalidPeriodException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EventService {

    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        validatePeriod(request);

        Event event = Event.builder()
                .name(request.name())
                .description(request.description())
                .venue(request.venue())
                .gateOpensAt(request.gateOpensAt())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .status(EventStatus.DRAFT)
                .build();

        return EventResponse.from(repository.save(event));
    }

    @Transactional
    public EventResponse publish(UUID publicId) {
        Event event = findEntity(publicId);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new InvalidEventStatusException(
                    "Apenas um evento em DRAFT pode ser publicado. Situacao atual: %s."
                            .formatted(event.getStatus()));
        }
        event.setStatus(EventStatus.PUBLISHED);
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> list(Pageable pageable) {
        return repository.findAll(pageable).map(EventResponse::from);
    }

    @Transactional(readOnly = true)
    public EventResponse findByPublicId(UUID publicId) {
        return EventResponse.from(findEntity(publicId));
    }

    /**
     * Devolve a entidade. Uso interno entre services (o lote precisa do evento);
     * a entidade nunca sai daqui para a camada web.
     */
    @Transactional(readOnly = true)
    public Event findEntity(UUID publicId) {
        return repository.findByPublicId(publicId)
                .orElseThrow(() -> new EventNotFoundException(publicId));
    }

    /** Mesmas condicoes de event_period_ck e event_gate_ck, checadas antes do banco. */
    private void validatePeriod(CreateEventRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new InvalidPeriodException("O fim do evento deve ser posterior ao inicio.");
        }
        if (request.gateOpensAt().isAfter(request.startsAt())) {
            throw new InvalidPeriodException(
                    "A abertura dos portoes nao pode ser posterior ao inicio do evento.");
        }
    }
}
