package br.com.portaria.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateEventRequest(

        @NotBlank(message = "O nome e obrigatorio")
        @Size(max = 120, message = "O nome deve ter no maximo 120 caracteres")
        String name,

        String description,

        @NotBlank(message = "O local e obrigatorio")
        @Size(max = 160, message = "O local deve ter no maximo 160 caracteres")
        String venue,

        @NotNull(message = "A abertura dos portoes e obrigatoria")
        LocalDateTime gateOpensAt,

        @NotNull(message = "O inicio do evento e obrigatorio")
        LocalDateTime startsAt,

        @NotNull(message = "O fim do evento e obrigatorio")
        LocalDateTime endsAt
) {
}
