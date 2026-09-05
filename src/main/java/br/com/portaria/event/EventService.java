package br.com.portaria.event;

import br.com.portaria.identity.AppUser;
import br.com.portaria.identity.CurrentUserService;
import br.com.portaria.identity.Role;
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
    private final CurrentUserService currentUser;

    public EventService(EventRepository repository, CurrentUserService currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        validatePeriod(request);

        Event event = Event.builder()
                .organizer(currentUser.require())
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
        Event event = findOwned(publicId);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new InvalidEventStatusException(
                    "Apenas um evento em DRAFT pode ser publicado. Situacao atual: %s."
                            .formatted(event.getStatus()));
        }
        event.setStatus(EventStatus.PUBLISHED);
        return EventResponse.from(event);
    }

    /**
     * Organizador ve os proprios eventos; qualquer outra conta ve os publicados.
     * A listagem nunca mistura os dois escopos.
     */
    @Transactional(readOnly = true)
    public Page<EventResponse> list(Pageable pageable) {
        AppUser user = currentUser.require();

        Page<Event> events = user.hasRole(Role.ORGANIZER)
                ? repository.findByOrganizerId(user.getId(), pageable)
                : repository.findByStatus(EventStatus.PUBLISHED, pageable);

        return events.map(EventResponse::from);
    }

    @Transactional(readOnly = true)
    public EventResponse findByPublicId(UUID publicId) {
        return EventResponse.from(findVisible(publicId));
    }

    /**
     * Evento que a conta atual pode enxergar: o proprio, se organizador, ou
     * qualquer um publicado.
     */
    @Transactional(readOnly = true)
    public Event findVisible(UUID publicId) {
        AppUser user = currentUser.require();
        Event event = findEntity(publicId);

        boolean owner = user.getId().equals(event.getOrganizer().getId());
        if (owner || event.getStatus() == EventStatus.PUBLISHED) {
            return event;
        }
        // rascunho de outro organizador: nao existe, do ponto de vista desta conta
        throw new EventNotFoundException(publicId);
    }

    /**
     * Evento do organizador autenticado.
     *
     * Evento de outro organizador devolve 404, e nao 403: um 403 confirmaria
     * que aquele identificador existe, o que permitiria mapear os eventos da
     * concorrencia so variando o UUID.
     */
    @Transactional(readOnly = true)
    public Event findOwned(UUID publicId) {
        AppUser user = currentUser.require();
        Event event = findEntity(publicId);

        if (!user.getId().equals(event.getOrganizer().getId())) {
            throw new EventNotFoundException(publicId);
        }
        return event;
    }

    /** Sem checagem de dono. Uso interno de fluxos que ja validaram o acesso. */
    @Transactional(readOnly = true)
    public Event findEntity(UUID publicId) {
        return repository.findByPublicId(publicId)
                .orElseThrow(() -> new EventNotFoundException(publicId));
    }

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
