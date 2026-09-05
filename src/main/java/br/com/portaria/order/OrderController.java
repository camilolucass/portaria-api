package br.com.portaria.order;

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
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Tag(name = "Pedidos", description = "Compra de ingressos e reserva de estoque")
@PreAuthorize("hasRole('BUYER')")
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @Operation(summary = "Cria um pedido",
            description = "Reserva o estoque na hora, antes do pagamento (RN-04), e expira em 15 minutos (RN-05). A quantidade de titulares deve ser igual a quantity.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido criado e estoque reservado"),
            @ApiResponse(responseCode = "400", description = "Campos invalidos ou titulares em numero diferente de quantity"),
            @ApiResponse(responseCode = "404", description = "Lote nao encontrado"),
            @ApiResponse(responseCode = "409", description = "Lote esgotado"),
            @ApiResponse(responseCode = "422", description = "Fora da janela de vendas ou evento nao publicado")
    })
    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestBody @Valid CreateOrderRequest request,
                                                UriComponentsBuilder uriBuilder) {
        OrderResponse created = service.create(request);
        var location = uriBuilder.path("/api/v1/orders/{publicId}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Confirma o pagamento",
            description = "Fase 1 apenas: simula a confirmacao. Gera um ingresso por titular (RN-07). Na Fase 3 e substituida pelo webhook do gateway.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido pago e ingressos emitidos"),
            @ApiResponse(responseCode = "404", description = "Pedido nao encontrado"),
            @ApiResponse(responseCode = "409", description = "O pedido nao esta PENDING")
    })
    /** Fase 1 apenas: simula a confirmacao do pagamento e dispara a RN-07. */
    @PostMapping("/{publicId}/pay")
    public OrderResponse pay(@PathVariable UUID publicId) {
        return service.pay(publicId);
    }

    @Operation(summary = "Cancela o pedido",
            description = "Cancela os ingressos ainda nao utilizados e devolve ao lote somente o estoque correspondente (RN-08).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido cancelado"),
            @ApiResponse(responseCode = "404", description = "Pedido nao encontrado"),
            @ApiResponse(responseCode = "409", description = "O pedido nao permite cancelamento")
    })
    /** RN-08. Rota fora da secao 6 do SPEC, que nao lista endpoint de cancelamento. */
    @PostMapping("/{publicId}/cancel")
    public OrderResponse cancel(@PathVariable UUID publicId) {
        return service.cancel(publicId);
    }

    @Operation(summary = "Busca um pedido",
            description = "Traz os ingressos emitidos, quando houver.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
            @ApiResponse(responseCode = "404", description = "Pedido nao encontrado")
    })
    @GetMapping("/{publicId}")
    public OrderResponse findById(@PathVariable UUID publicId) {
        return service.findByPublicId(publicId);
    }
}
