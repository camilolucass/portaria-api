package br.com.portaria.ticket;

import java.util.UUID;

/** Ingresso como ele aparece dentro do pedido. O code assinado entra na Etapa 4. */
public record TicketSummaryResponse(
        UUID id,
        String holderName,
        String holderDocument,
        TicketStatus status
) {

    public static TicketSummaryResponse from(Ticket ticket) {
        return new TicketSummaryResponse(
                ticket.getPublicId(),
                ticket.getHolderName(),
                ticket.getHolderDocument(),
                ticket.getStatus());
    }
}
