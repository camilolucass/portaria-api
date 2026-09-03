package br.com.portaria.batch;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventPublicId}/batches")
public class TicketBatchController {

    private final TicketBatchService service;

    public TicketBatchController(TicketBatchService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BatchResponse> create(@PathVariable UUID eventPublicId,
                                                @RequestBody @Valid CreateBatchRequest request) {
        return ResponseEntity.status(201).body(service.create(eventPublicId, request));
    }

    @GetMapping
    public List<BatchResponse> listByEvent(@PathVariable UUID eventPublicId) {
        return service.listByEvent(eventPublicId);
    }
}
