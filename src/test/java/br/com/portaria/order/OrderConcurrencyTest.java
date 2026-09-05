package br.com.portaria.order;

import br.com.portaria.AbstractDatabaseTest;
import br.com.portaria.batch.TicketBatch;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.event.EventRepository;
import br.com.portaria.identity.AppUser;
import br.com.portaria.identity.Role;
import br.com.portaria.shared.exception.SoldOutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TC-01 — 10 vagas, 20 threads comprando 1 ingresso ao mesmo tempo:
 * exatamente 10 sucessos, 10 SoldOutException e sold_quantity = 10.
 *
 * E o teste que prova o P1. Sem o UPDATE condicional da 7.1 ele falha de forma
 * intermitente, que e exatamente o que torna oversell dificil de achar em
 * producao.
 */
class OrderConcurrencyTest extends AbstractDatabaseTest {

    private static final int SLOTS = 10;
    private static final int THREADS = 20;

    @Autowired
    private OrderService orderService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketBatchRepository batchRepository;

    @Test
    void naoDeveVenderMaisIngressosDoQueOLoteComporta() throws Exception {
        AppUser organizer = createUser("organizadora@exemplo.com", Role.ORGANIZER);
        TicketBatch batch = OrderTestFixtures.publishedBatchWithSlots(
                eventRepository, batchRepository, organizer, SLOTS, 4500);

        // uma conta por thread: 20 compradores diferentes disputando o mesmo lote
        var buyers = new java.util.ArrayList<AppUser>();
        for (int i = 0; i < THREADS; i++) {
            buyers.add(createUser("comprador" + i + "@exemplo.com", Role.BUYER));
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            final String document = "1234567890" + i;
            final AppUser buyer = buyers.get(i);
            pool.submit(() -> {
                try {
                    // o SecurityContext e ThreadLocal: cada thread autentica a sua conta
                    authenticateAs(buyer);
                    start.await();
                    orderService.create(OrderTestFixtures.orderFor(batch, 1, document));
                    created.incrementAndGet();
                } catch (SoldOutException expected) {
                    soldOut.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();                                   // largada simultanea
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(created.get()).isEqualTo(SLOTS);
        assertThat(soldOut.get()).isEqualTo(THREADS - SLOTS);

        TicketBatch reloaded = batchRepository.findById(batch.getId()).orElseThrow();
        assertThat(reloaded.getSoldQuantity()).isEqualTo(SLOTS);
        assertThat(reloaded.getSoldQuantity()).isLessThanOrEqualTo(reloaded.getTotalQuantity());
    }
}
