package br.com.portaria.checkin;

import br.com.portaria.AbstractDatabaseTest;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.event.EventRepository;
import br.com.portaria.order.BuyerRepository;
import br.com.portaria.order.OrderRepository;
import br.com.portaria.shared.exception.TicketAlreadyUsedException;
import br.com.portaria.ticket.QrCodeSigner;
import br.com.portaria.ticket.Ticket;
import br.com.portaria.ticket.TicketRepository;
import br.com.portaria.ticket.TicketStatus;
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
 * TC-02 — 20 portarias tentando o mesmo codigo ao mesmo tempo: exatamente uma
 * entrada liberada, 19 recusadas.
 *
 * RN-13: isso nao e meta de desempenho, e requisito funcional. E o teste que
 * prova o P3.
 */
class CheckinConcurrencyTest extends AbstractDatabaseTest {

    private static final int THREADS = 20;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private QrCodeSigner signer;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketBatchRepository batchRepository;

    @Autowired
    private BuyerRepository buyerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void apenasUmaPortariaPodeLiberarOMesmoIngresso() throws Exception {
        Ticket ticket = CheckinTestFixtures.issuedTicketWithOpenGate(
                eventRepository, batchRepository, buyerRepository, orderRepository,
                ticketRepository, "12345678900");
        String code = signer.sign(ticket.getPublicId());

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger granted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        // toda recusa tem de saber DIZER quando o ingresso foi usado. E o que
        // exige clearAutomatically = true no @Modifying: sem ele o findById
        // seguinte devolve a copia em cache, carregada antes de a outra thread
        // gravar, e a mensagem sai sem horario.
        AtomicInteger rejectedWithoutTimestamp = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            final String operator = "portaria-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    checkinService.checkIn(code, operator);
                    granted.incrementAndGet();
                } catch (TicketAlreadyUsedException expected) {
                    rejected.incrementAndGet();
                    if (expected.getCheckedInAt() == null) {
                        rejectedWithoutTimestamp.incrementAndGet();
                    }
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

        assertThat(granted.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(THREADS - 1);
        assertThat(rejectedWithoutTimestamp.get()).isZero();

        Ticket used = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(used.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(used.getCheckedInAt()).isNotNull();
        assertThat(used.getCheckedInBy()).isNotNull();
    }
}
