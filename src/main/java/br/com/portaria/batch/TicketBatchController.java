package br.com.portaria.batch;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Lotes", description = "Lotes de ingressos de um evento")
@RestController
@RequestMapping("/api/v1/events/{eventPublicId}/batches")
public class TicketBatchController {

    private final TicketBatchService service;

    public TicketBatchController(TicketBatchService service) {
        this.service = service;
    }

    @Operation(summary = "Cria um lote",
            description = "O fim das vendas nao pode ultrapassar o inicio do evento (RN-02).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lote criado"),
            @ApiResponse(responseCode = "400", description = "Campos invalidos"),
            @ApiResponse(responseCode = "404", description = "Evento nao encontrado"),
            @ApiResponse(responseCode = "422", description = "Periodo de vendas incoerente")
    })
    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping
    public ResponseEntity<BatchResponse> create(@PathVariable UUID eventPublicId,
                                                @RequestBody @Valid CreateBatchRequest request) {
        return ResponseEntity.status(201).body(service.create(eventPublicId, request));
    }

    @Operation(summary = "Lista os lotes do evento",
            description = "Cada item traz availableQuantity = totalQuantity - soldQuantity.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lotes do evento"),
            @ApiResponse(responseCode = "404", description = "Evento nao encontrado")
    })
    @GetMapping
    public List<BatchResponse> listByEvent(@PathVariable UUID eventPublicId) {
        return service.listByEvent(eventPublicId);
    }
}
