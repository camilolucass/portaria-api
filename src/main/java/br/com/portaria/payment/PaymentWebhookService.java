package br.com.portaria.payment;

import br.com.portaria.order.OrderRepository;
import br.com.portaria.order.OrderService;
import br.com.portaria.order.OrderStatus;
import br.com.portaria.order.PurchaseOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Problema P4 — a mesma notificacao chegando varias vezes.
 *
 * Gateway de pagamento promete "pelo menos uma entrega", nunca "exatamente
 * uma". Timeout do nosso lado, deploy no meio do processamento, retry
 * programado: a mesma notificacao volta. Sem defesa, o pedido e pago de novo e
 * os ingressos sao emitidos de novo.
 *
 * Duas regras sustentam este fluxo:
 *
 * 1. A notificacao nao decide nada. Ela traz um identificador; a situacao do
 *    pagamento vem de uma consulta ao gateway. Quem posta aqui e a internet
 *    inteira, e um corpo dizendo "aprovado" nao prova pagamento nenhum.
 * 2. O registro da notificacao e uma escrita condicional. A primeira grava, as
 *    repeticoes afetam zero linhas e param ali — mesmo padrao da reserva de
 *    estoque e do check-in.
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentGateway gateway;
    private final PaymentEventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public PaymentWebhookService(PaymentGateway gateway,
                                 PaymentEventRepository eventRepository,
                                 OrderRepository orderRepository,
                                 OrderService orderService) {
        this.gateway = gateway;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    /**
     * Sempre devolve sucesso, inclusive para notificacao repetida ou
     * desconhecida. Responder erro faria o gateway reenviar em backoff por
     * horas por algo que ja esta resolvido — a resposta diz "recebi", nao
     * "concordo".
     */
    @Transactional
    public WebhookResult process(String externalId) {
        Optional<GatewayPayment> found = gateway.find(externalId);
        if (found.isEmpty()) {
            // pagamento que o gateway nao conhece: nao ha o que processar, e
            // nao ha por que pedir reenvio
            log.warn("Notificacao para pagamento desconhecido: {}", externalId);
            return WebhookResult.IGNORED;
        }

        GatewayPayment payment = found.get();
        if (payment.status() != PaymentStatus.APPROVED) {
            return WebhookResult.IGNORED;
        }

        PurchaseOrder order = orderRepository.findByPaymentReference(payment.paymentReference())
                .orElse(null);
        if (order == null) {
            log.warn("Pagamento {} aponta para pedido inexistente", externalId);
            return WebhookResult.IGNORED;
        }

        // a escrita condicional decide: 1 linha = primeira vez, 0 = repeticao
        int recorded = eventRepository.recordIfNew(
                externalId, order.getId(), payment.status().name());

        if (recorded == 0) {
            log.info("Notificacao {} ja processada, ignorando", externalId);
            return WebhookResult.DUPLICATE;
        }

        // o pedido pode ter sido pago por outro caminho ou expirado antes;
        // o registro ja esta gravado, entao a repeticao nao volta aqui
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Pagamento {} recebido para pedido em {}", externalId, order.getStatus());
            return WebhookResult.IGNORED;
        }

        orderService.confirmPayment(order);
        return WebhookResult.PROCESSED;
    }

    public enum WebhookResult {
        /** Primeira vez: o pedido foi pago e os ingressos emitidos. */
        PROCESSED,
        /** Notificacao ja registrada antes. Nada foi refeito. */
        DUPLICATE,
        /** Pagamento desconhecido, nao aprovado, ou pedido fora de PENDING. */
        IGNORED
    }
}
