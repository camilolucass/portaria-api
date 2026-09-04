package br.com.portaria.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * ATENCAO — esta rota devolve receita. Na Fase 2 ela e a primeira a exigir o
 * papel ORGANIZER: o SPEC diz que o operador de portaria nunca ve dado
 * financeiro, e hoje, sem autenticacao, ela esta aberta como todas as outras.
 */
@Tag(name = "Estatisticas", description = "Painel do organizador")
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
