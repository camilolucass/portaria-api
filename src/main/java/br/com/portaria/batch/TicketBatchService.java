package br.com.portaria.batch;

import br.com.portaria.event.Event;
import br.com.portaria.event.EventService;
import br.com.portaria.event.EventStatus;
import br.com.portaria.shared.exception.BatchNotFoundException;
import br.com.portaria.shared.exception.EventNotPublishedException;
import br.com.portaria.shared.exception.InvalidPeriodException;
import br.com.portaria.shared.exception.SalesWindowClosedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TicketBatchService {

    private final TicketBatchRepository repository;
    private final EventService eventService;

    public TicketBatchService(TicketBatchRepository repository, EventService eventService) {
        this.repository = repository;
        this.eventService = eventService;
    }

    @Transactional
    public BatchResponse create(UUID eventPublicId, CreateBatchRequest request) {
        Event event = eventService.findOwned(eventPublicId);
        validateSalesPeriod(request, event);

        TicketBatch batch = TicketBatch.builder()
                .event(event)
                .name(request.name())
                .priceCents(request.priceCents())
                .totalQuantity(request.totalQuantity())
                .soldQuantity(0)
                .salesStart(request.salesStart())
                .salesEnd(request.salesEnd())
                .build();

        return BatchResponse.from(repository.save(batch));
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> listByEvent(UUID eventPublicId) {
        // quem compra precisa ver os lotes; findVisible cobre publicado ou proprio
        Event event = eventService.findVisible(eventPublicId);
        return repository.findByEventIdOrderBySalesStartAsc(event.getId())
                .stream()
                .map(BatchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketBatch findEntity(UUID publicId) {
        return repository.findByPublicId(publicId)
                .orElseThrow(() -> new BatchNotFoundException(publicId));
    }

    /**
     * RN-01 e RN-02, na forma que o TC-10 exercita: o lote so aceita venda se o
     * evento estiver PUBLISHED e o instante estiver dentro de
     * [sales_start, sales_end]. Fora disso, 422.
     */
    public void assertOnSale(TicketBatch batch, LocalDateTime now) {
        Event event = batch.getEvent();

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventNotPublishedException(
                    "O evento \"%s\" nao esta publicado. Situacao atual: %s."
                            .formatted(event.getName(), event.getStatus()));
        }
        if (now.isBefore(batch.getSalesStart())) {
            throw new SalesWindowClosedException(
                    "As vendas do lote \"%s\" ainda nao comecaram.".formatted(batch.getName()));
        }
        if (now.isAfter(batch.getSalesEnd())) {
            throw new SalesWindowClosedException(
                    "As vendas do lote \"%s\" ja encerraram.".formatted(batch.getName()));
        }
    }

    /** batch_sales_ck e RN-02, checados antes do banco para devolver 422 com contexto. */
    private void validateSalesPeriod(CreateBatchRequest request, Event event) {
        if (!request.salesEnd().isAfter(request.salesStart())) {
            throw new InvalidPeriodException(
                    "O fim das vendas deve ser posterior ao inicio das vendas.");
        }
        if (request.salesEnd().isAfter(event.getStartsAt())) {
            throw new InvalidPeriodException(
                    "O fim das vendas nao pode ultrapassar o inicio do evento.");
        }
    }
}
