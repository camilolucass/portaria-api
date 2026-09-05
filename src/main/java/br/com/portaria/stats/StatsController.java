package br.com.portaria.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Devolve receita. Exige ORGANIZER e so entrega os proprios eventos: o SPEC
 * determina que o operador de portaria nunca veja dado financeiro.
 */
@Tag(name = "Estatisticas", description = "Painel do organizador")
@PreAuthorize("hasRole('ORGANIZER')")
@RestController
@RequestMapping("/api/v1/events/{eventPublicId}/stats")
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @Operation(summary = "Estatisticas do evento",
            description = "Emitidos, presentes e receita, com detalhamento por lote. "
                    + "Contem dado financeiro: restrita ao papel ORGANIZER a partir da Fase 2.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estatisticas do evento"),
            @ApiResponse(responseCode = "404", description = "Evento nao encontrado")
    })
    @GetMapping
    public EventStatsResponse stats(@PathVariable UUID eventPublicId) {
        return service.of(eventPublicId);
    }
}
