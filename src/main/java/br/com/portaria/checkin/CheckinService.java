package br.com.portaria.checkin;

import br.com.portaria.event.Event;
import br.com.portaria.event.EventStaffRepository;
import br.com.portaria.identity.AppUser;
import br.com.portaria.identity.CurrentUserService;
import br.com.portaria.shared.exception.GateWindowClosedException;
import br.com.portaria.shared.exception.InvalidTicketCodeException;
import br.com.portaria.shared.exception.NotAssignedToEventException;
import br.com.portaria.shared.exception.TicketAlreadyUsedException;
import br.com.portaria.shared.exception.TicketCancelledException;
import br.com.portaria.ticket.QrCodeSigner;
import br.com.portaria.ticket.Ticket;
import br.com.portaria.ticket.TicketRepository;
import br.com.portaria.ticket.TicketStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** SPEC 7.3 — check-in atomico (P3), RN-09 a RN-13, com autorizacao da Fase 2. */
@Service
public class CheckinService {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm");

    private final TicketRepository ticketRepository;
    private final EventStaffRepository eventStaffRepository;
    private final CurrentUserService currentUser;
    private final QrCodeSigner signer;

    public CheckinService(TicketRepository ticketRepository,
                          EventStaffRepository eventStaffRepository,
                          CurrentUserService currentUser,
                          QrCodeSigner signer) {
        this.ticketRepository = ticketRepository;
        this.eventStaffRepository = eventStaffRepository;
        this.currentUser = currentUser;
        this.signer = signer;
    }

    @Transactional
    public CheckinResult checkIn(String code) {
        AppUser operator = currentUser.require();
        UUID publicId = signer.verifyAndExtract(code);                  // RN-09, RN-10

        Ticket ticket = ticketRepository.findByPublicId(publicId)
                .orElseThrow(InvalidTicketCodeException::new);

        Event event = ticket.getBatch().getEvent();
        assertAssignedToEvent(operator, event);                         // Fase 2
        validateGateWindow(event);                                      // RN-12

        LocalDateTime now = LocalDateTime.now();
        int updated = ticketRepository.checkIn(
                ticket.getId(), now, operator, TicketStatus.USED, TicketStatus.ISSUED);

        if (updated == 0) {                                             // RN-11
            Ticket current = ticketRepository.findById(ticket.getId()).orElseThrow();
            if (current.getStatus() == TicketStatus.CANCELLED) {
                throw new TicketCancelledException();
            }
            throw new TicketAlreadyUsedException(
                    current.getCheckedInAt(),
                    current.getCheckedInBy() == null ? null : current.getCheckedInBy().getName());
        }

        return CheckinResult.granted(ticket, now);
    }

    /**
     * O operador so valida entrada dos eventos aos quais esta vinculado. Sem
     * isso, uma portaria contratada para um evento liberaria entrada em
     * qualquer outro, bastando ler o QR.
     */
    private void assertAssignedToEvent(AppUser operator, Event event) {
        if (!eventStaffRepository.existsByEventIdAndUserId(event.getId(), operator.getId())) {
            throw new NotAssignedToEventException(event.getName());
        }
    }

    /** RN-12 — a entrada so vale entre a abertura dos portoes e o fim do evento. */
    private void validateGateWindow(Event event) {
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(event.getGateOpensAt())) {
            throw new GateWindowClosedException(
                    "Os portoes abrem em %s.".formatted(FORMAT.format(event.getGateOpensAt())));
        }
        if (now.isAfter(event.getEndsAt())) {
            throw new GateWindowClosedException(
                    "O evento terminou em %s.".formatted(FORMAT.format(event.getEndsAt())));
        }
    }
}
