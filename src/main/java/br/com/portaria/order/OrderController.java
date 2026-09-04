package br.com.portaria.order;

import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody @Valid CreateOrderRequest request,
                                                UriComponentsBuilder uriBuilder) {
        OrderResponse created = service.create(request);
        var location = uriBuilder.path("/api/v1/orders/{publicId}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Fase 1 apenas: simula a confirmacao do pagamento e dispara a RN-07. */
    @PostMapping("/{publicId}/pay")
    public OrderResponse pay(@PathVariable UUID publicId) {
        return service.pay(publicId);
    }

    /** RN-08. Rota fora da secao 6 do SPEC, que nao lista endpoint de cancelamento. */
    @PostMapping("/{publicId}/cancel")
    public OrderResponse cancel(@PathVariable UUID publicId) {
        return service.cancel(publicId);
    }

    @GetMapping("/{publicId}")
    public OrderResponse findById(@PathVariable UUID publicId) {
        return service.findByPublicId(publicId);
    }
}
