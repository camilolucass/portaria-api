package br.com.portaria.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String name,
        String description,
        String venue,
        LocalDateTime gateOpensAt,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        EventStatus status,
        LocalDateTime createdAt
) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getPublicId(),
                event.getName(),
                event.getDescription(),
                event.getVenue(),
                event.getGateOpensAt(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getStatus(),
                event.getCreatedAt());
    }
}
