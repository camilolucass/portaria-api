package br.com.portaria.order;

import br.com.portaria.batch.TicketBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** RN-06 / SPEC 7.4 — pedido PENDING vencido vira EXPIRED e devolve o estoque. */
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
        List<PurchaseOrder> expired = orderRepository.findByStatusAndExpiresAtBefore(
                OrderStatus.PENDING, LocalDateTime.now());

        for (PurchaseOrder order : expired) {
            batchRepository.release(order.getBatch().getId(), order.getQuantity());
            order.setStatus(OrderStatus.EXPIRED);
        }
        if (!expired.isEmpty()) {
            log.info("Expirados {} pedidos, estoque devolvido", expired.size());
        }
    }
}
