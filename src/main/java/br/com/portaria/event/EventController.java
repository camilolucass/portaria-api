package br.com.portaria.event;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@RequestBody @Valid CreateEventRequest request,
                                                UriComponentsBuilder uriBuilder) {
        EventResponse created = service.create(request);
        var location = uriBuilder.path("/api/v1/events/{publicId}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/{publicId}/publish")
    public EventResponse publish(@PathVariable UUID publicId) {
        return service.publish(publicId);
    }

    /**
     * PagedModel explicito: o Spring avisa que serializar PageImpl direto e um
     * formato instavel. Aqui o contrato e {content, page:{size,number,...}}.
     */
    @GetMapping
    public PagedModel<EventResponse> list(Pageable pageable) {
        return new PagedModel<>(service.list(pageable));
    }

    @GetMapping("/{publicId}")
    public EventResponse findById(@PathVariable UUID publicId) {
        return service.findByPublicId(publicId);
    }
}
