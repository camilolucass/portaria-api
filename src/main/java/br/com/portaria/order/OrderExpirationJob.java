package br.com.portaria.order;

import br.com.portaria.batch.TicketBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RN-06 / SPEC 7.4 — pedido PENDING vencido vira EXPIRED e devolve o estoque.
 *
 * O job roda em toda instancia da aplicacao. Devolver estoque direto, como a
 * leitura ingenua do 7.4 sugere, faz o saldo voltar uma vez por instancia
 * assim que houver mais de uma replica. Por isso o pedido e primeiro
 * reivindicado por um UPDATE condicional: so quem realmente mudou a linha
 * devolve o estoque.
 */
@Component
public class OrderExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationJob.class);

    private final OrderRepository orderRepository;
    private final TicketBatchRepository batchRepository;

    public OrderExpirationJob(OrderRepository orderRepository, TicketBatchRepository batchRepository) {
        this.orderRepository = orderRepository;
        this.batchRepository = batchRepository;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expirePendingOrders() {
        List<PurchaseOrder> candidates = orderRepository.findByStatusAndExpiresAtBeforeOrderByIdAsc(
                OrderStatus.PENDING, LocalDateTime.now());

        int expired = 0;
        for (PurchaseOrder order : candidates) {
            // lidos antes do UPDATE: depois dele a entidade em memoria esta defasada
            Long batchId = order.getBatch().getId();
            int quantity = order.getQuantity();

            boolean claimed = orderRepository.markExpired(
                    order.getId(), OrderStatus.EXPIRED, OrderStatus.PENDING) == 1;

            if (claimed) {
                batchRepository.release(batchId, quantity);
                expired++;
            }
        }

        if (expired > 0) {
            log.info("Expirados {} pedidos, estoque devolvido", expired);
        }
    }
}
