package br.com.portaria.order;

import br.com.portaria.AbstractDatabaseTest;
import br.com.portaria.batch.TicketBatch;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.event.EventRepository;
import br.com.portaria.identity.AppUser;
import br.com.portaria.identity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O job de expiracao roda em toda instancia da aplicacao. Com duas ou mais
 * replicas — o cenario normal de qualquer deploy com redundancia — as duas leem
 * os mesmos pedidos PENDING vencidos e as duas devolvem estoque ao lote.
 *
 * O estoque volta em dobro, sold_quantity fica menor do que a realidade, e o
 * lote passa a aceitar vendas que nao existem. E o mesmo oversell do P1,
 * entrando pela unica porta onde o UPDATE condicional nao estava aplicado.
 *
 * O guard "AND soldQuantity >= :quantity" do release nao protege: ele so impede
 * o estoque de ficar negativo. Se ha outro pedido pago segurando estoque, a
 * devolucao em dobro cabe dentro do saldo e passa despercebida.
 */
class OrderExpirationConcurrencyTest extends AbstractDatabaseTest {

    private static final int INSTANCES = 8;

    private static final int PAID_QUANTITY = 5;
    private static final int EXPIRED_QUANTITY = 4;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderExpirationJob expirationJob;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketBatchRepository batchRepository;

    @Test
    void estoqueNaoPodeVoltarEmDobroQuandoVariasInstanciasExpiramJuntas() throws Exception {
        AppUser organizer = createUser("organizadora@exemplo.com", Role.ORGANIZER);
        AppUser buyer = createUser("compradora@exemplo.com", Role.BUYER);
        authenticateAs(buyer);

        TicketBatch batch = OrderTestFixtures.publishedBatchWithSlots(
                eventRepository, batchRepository, organizer, 100, 4500);

        // um pedido pago, que segura estoque de verdade e nunca deve ser mexido
        var paid = orderService.create(
                OrderTestFixtures.orderFor(batch, PAID_QUANTITY, "11111111111"));
        orderService.pay(paid.id());

        // um pedido pendente e vencido, que deve ser devolvido exatamente uma vez
        var pending = orderService.create(
                OrderTestFixtures.orderFor(batch, EXPIRED_QUANTITY, "22222222222"));
        var order = orderRepository.findByPublicId(pending.id()).orElseThrow();
        order.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        orderRepository.save(order);

        assertThat(soldQuantity(batch)).isEqualTo(PAID_QUANTITY + EXPIRED_QUANTITY);

        ExecutorService pool = Executors.newFixedThreadPool(INSTANCES);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(INSTANCES);

        for (int i = 0; i < INSTANCES; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    expirationJob.expirePendingOrders();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ignored) {
                    // uma instancia perdendo a corrida e esperado; o que importa e o saldo
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(orderRepository.findByPublicId(pending.id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.EXPIRED);

        // o pedido pago continua segurando o seu estoque, e o vencido devolveu uma vez so
        assertThat(soldQuantity(batch))
                .as("estoque devolvido mais de uma vez pelas instancias concorrentes")
                .isEqualTo(PAID_QUANTITY);
    }

    private int soldQuantity(TicketBatch batch) {
        return batchRepository.findById(batch.getId()).orElseThrow().getSoldQuantity();
    }
}
