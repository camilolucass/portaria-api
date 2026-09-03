package br.com.portaria.batch;

import br.com.portaria.AbstractIntegrationTest;
import br.com.portaria.event.Event;
import br.com.portaria.event.EventRepository;
import br.com.portaria.event.EventStatus;
import br.com.portaria.shared.exception.EventNotPublishedException;
import br.com.portaria.shared.exception.SalesWindowClosedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TC-10 — compra em lote fora da janela de vendas devolve 422.
 *
 * A regra (RN-01 e RN-02) vive em TicketBatchService.assertOnSale e e o que o
 * OrderService da Etapa 3 vai chamar antes de reservar estoque.
 */
@Transactional
class SalesWindowTest extends AbstractIntegrationTest {

    private static final LocalDateTime GATE  = LocalDateTime.of(2026, 10, 10, 21, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 10, 22, 0);
    private static final LocalDateTime END   = LocalDateTime.of(2026, 10, 11, 4, 0);

    private static final LocalDateTime SALES_START = LocalDateTime.of(2026, 9, 10, 0, 0);
    private static final LocalDateTime SALES_END   = LocalDateTime.of(2026, 10, 5, 23, 59);

    @Autowired
    private TicketBatchService batchService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketBatchRepository batchRepository;

    @Test
    void deveRecusarCompraAntesDoInicioDasVendas() {
        TicketBatch batch = publishedBatch();

        assertThatThrownBy(() -> batchService.assertOnSale(batch, SALES_START.minusDays(1)))
                .isInstanceOf(SalesWindowClosedException.class)
                .satisfies(ex -> assertThat(((SalesWindowClosedException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
                .hasMessageContaining("ainda nao comecaram");
    }

    @Test
    void deveRecusarCompraDepoisDoFimDasVendas() {
        TicketBatch batch = publishedBatch();

        assertThatThrownBy(() -> batchService.assertOnSale(batch, SALES_END.plusSeconds(1)))
                .isInstanceOf(SalesWindowClosedException.class)
                .satisfies(ex -> assertThat(((SalesWindowClosedException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
                .hasMessageContaining("ja encerraram");
    }

    /** RN-01: mesmo dentro da janela, evento em DRAFT nao vende. */
    @Test
    void deveRecusarCompraEmEventoNaoPublicado() {
        TicketBatch batch = batchFor(EventStatus.DRAFT);

        assertThatThrownBy(() -> batchService.assertOnSale(batch, SALES_START.plusDays(1)))
                .isInstanceOf(EventNotPublishedException.class)
                .satisfies(ex -> assertThat(((EventNotPublishedException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void deveAceitarCompraDentroDaJanela() {
        TicketBatch batch = publishedBatch();

        assertThatCode(() -> batchService.assertOnSale(batch, SALES_START.plusDays(1)))
                .doesNotThrowAnyException();
    }

    private TicketBatch publishedBatch() {
        return batchFor(EventStatus.PUBLISHED);
    }

    private TicketBatch batchFor(EventStatus status) {
        Event event = eventRepository.save(Event.builder()
                .name("Festa Universitaria 2026")
                .venue("Centro de Eventos, Orleans/SC")
                .gateOpensAt(GATE)
                .startsAt(START)
                .endsAt(END)
                .status(status)
                .build());

        return batchRepository.save(TicketBatch.builder()
                .event(event)
                .name("1o lote")
                .priceCents(4500)
                .totalQuantity(200)
                .soldQuantity(0)
                .salesStart(SALES_START)
                .salesEnd(SALES_END)
                .build());
    }
}
