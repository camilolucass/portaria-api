package br.com.portaria.batch;

import java.time.LocalDateTime;
import java.util.UUID;

public record BatchResponse(
        UUID id,
        String name,
        int priceCents,
        int totalQuantity,
        int soldQuantity,
        int availableQuantity,
        LocalDateTime salesStart,
        LocalDateTime salesEnd
) {

    public static BatchResponse from(TicketBatch batch) {
        return new BatchResponse(
                batch.getPublicId(),
                batch.getName(),
                batch.getPriceCents(),
                batch.getTotalQuantity(),
                batch.getSoldQuantity(),
                batch.getTotalQuantity() - batch.getSoldQuantity(),
                batch.getSalesStart(),
                batch.getSalesEnd());
    }
}
