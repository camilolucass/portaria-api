package br.com.portaria.checkin;

import br.com.portaria.event.Event;
import br.com.portaria.shared.exception.GateWindowClosedException;
import br.com.portaria.shared.exception.InvalidTicketCodeException;
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

/** SPEC 7.3 — check-in atomico (problema P3), RN-09 a RN-13. */
@Service
public class CheckinService {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm");

    private final TicketRepository ticketRepository;
    private final QrCodeSigner signer;

    public CheckinService(TicketRepository ticketRepository, QrCodeSigner signer) {
        this.ticketRepository = ticketRepository;
        this.signer = signer;
    }

    @Transactional
    public CheckinResult checkIn(String code, String operator) {
        UUID publicId = signer.verifyAndExtract(code);                  // RN-09, RN-10

        // ingresso inexistente sai com a mesma excecao de assinatura invalida:
        // a resposta nao pode revelar que o codigo era autentico (RN-10)
        Ticket ticket = ticketRepository.findByPublicId(publicId)
                .orElseThrow(InvalidTicketCodeException::new);

        validateGateWindow(ticket);                                     // RN-12

        LocalDateTime now = LocalDateTime.now();
        int updated = ticketRepository.checkIn(
                ticket.getId(), now, operator, TicketStatus.USED, TicketStatus.ISSUED);

        if (updated == 0) {                                             // RN-11
            Ticket current = ticketRepository.findById(ticket.getId()).orElseThrow();
            if (current.getStatus() == TicketStatus.CANCELLED) {
                throw new TicketCancelledException();
            }
            throw new TicketAlreadyUsedException(current.getCheckedInAt(), current.getCheckedInBy());
        }

        return CheckinResult.granted(ticket, now);
    }

    /** RN-12 — a entrada so vale entre a abertura dos portoes e o fim do evento. */
    private void validateGateWindow(Ticket ticket) {
        Event event = ticket.getBatch().getEvent();
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
