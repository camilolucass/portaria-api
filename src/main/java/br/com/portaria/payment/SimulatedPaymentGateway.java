package br.com.portaria.payment;

import br.com.portaria.order.OrderRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Gateway simulado: o externalId da notificacao e a payment_reference do pedido,
 * e qualquer pagamento encontrado e considerado aprovado.
 *
 * Existe para o fluxo de webhook ser exercitavel sem credencial de sandbox. O
 * que ele NAO simula e o essencial: a consulta acontece de verdade, e a
 * situacao vem dela e nao do corpo da requisicao. Trocar por Mercado Pago e
 * implementar esta interface — nada mais no caminho muda.
 */
@Component
@ConditionalOnMissingBean(name = "mercadoPagoGateway")
public class SimulatedPaymentGateway implements PaymentGateway {

    private final OrderRepository orderRepository;

    public SimulatedPaymentGateway(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GatewayPayment> find(String externalId) {
        UUID reference;
        try {
            reference = UUID.fromString(externalId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return orderRepository.findByPaymentReference(reference)
                .map(order -> new GatewayPayment(externalId, reference, PaymentStatus.APPROVED));
    }
}
