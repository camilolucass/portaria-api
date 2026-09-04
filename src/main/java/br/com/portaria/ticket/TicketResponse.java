package br.com.portaria.ticket;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        String holderName,
        String holderDocument,
        TicketStatus status,
        String eventName,
        String batchName,
        LocalDateTime checkedInAt,
        String code
) {

    static TicketResponse of(Ticket ticket, String code) {
        return new TicketResponse(
                ticket.getPublicId(),
                ticket.getHolderName(),
                ticket.getHolderDocument(),
                ticket.getStatus(),
                ticket.getBatch().getEvent().getName(),
                ticket.getBatch().getName(),
                ticket.getCheckedInAt(),
                code);
    }
}
