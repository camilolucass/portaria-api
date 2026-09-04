package br.com.portaria.order;

import br.com.portaria.ticket.Ticket;
import br.com.portaria.ticket.TicketSummaryResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID batchId,
        String batchName,
        String eventName,
        int quantity,
        int totalCents,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime paidAt,
        BuyerResponse buyer,
        List<TicketSummaryResponse> tickets
) {

    /**
     * Montado dentro do service, com a transacao aberta: open-in-view esta
     * desligado, entao nada de lazy pode ser tocado depois daqui.
     */
    public static OrderResponse of(PurchaseOrder order, List<Ticket> tickets) {
        return new OrderResponse(
                order.getPublicId(),
                order.getBatch().getPublicId(),
                order.getBatch().getName(),
                order.getBatch().getEvent().getName(),
                order.getQuantity(),
                order.getTotalCents(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getExpiresAt(),
                order.getPaidAt(),
                BuyerResponse.from(order.getBuyer()),
                tickets.stream().map(TicketSummaryResponse::from).toList());
    }
}
