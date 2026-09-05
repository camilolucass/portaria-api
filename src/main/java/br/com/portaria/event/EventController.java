package br.com.portaria.event;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Tag(name = "Eventos", description = "Criacao, publicacao e consulta de eventos")
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @Operation(summary = "Cria um evento",
            description = "O evento nasce em DRAFT e so aceita venda depois de publicado (RN-01).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Evento criado"),
            @ApiResponse(responseCode = "400", description = "Campos invalidos"),
            @ApiResponse(responseCode = "422", description = "Periodo incoerente entre portao, inicio e fim")
    })
    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping
    public ResponseEntity<EventResponse> create(@RequestBody @Valid CreateEventRequest request,
                                                UriComponentsBuilder uriBuilder) {
        EventResponse created = service.create(request);
        var location = uriBuilder.path("/api/v1/events/{publicId}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Publica o evento",
            description = "Transicao DRAFT para PUBLISHED. Um evento ja publicado devolve 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento publicado"),
            @ApiResponse(responseCode = "404", description = "Evento nao encontrado"),
            @ApiResponse(responseCode = "409", description = "O evento nao esta em DRAFT")
    })
    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping("/{publicId}/publish")
    public EventResponse publish(@PathVariable UUID publicId) {
        return service.publish(publicId);
    }

    @Operation(summary = "Lista os eventos",
            description = "Paginado. Aceita page, size e sort.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de eventos")
    })
    /**
     * PagedModel explicito: o Spring avisa que serializar PageImpl direto e um
     * formato instavel. Aqui o contrato e {content, page:{size,number,...}}.
     */
    @GetMapping
    public PagedModel<EventResponse> list(Pageable pageable) {
        return new PagedModel<>(service.list(pageable));
    }

    @Operation(summary = "Busca um evento",
            description = "Consulta pelo identificador publico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento encontrado"),
            @ApiResponse(responseCode = "404", description = "Evento nao encontrado")
    })
    @GetMapping("/{publicId}")
    public EventResponse findById(@PathVariable UUID publicId) {
        return service.findByPublicId(publicId);
    }
}
