package br.com.portaria.stats;

import br.com.portaria.AbstractDatabaseTest;
import br.com.portaria.batch.TicketBatch;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.checkin.CheckinRequest;
import br.com.portaria.event.Event;
import br.com.portaria.event.EventRepository;
import br.com.portaria.event.EventStatus;
import br.com.portaria.order.BuyerRequest;
import br.com.portaria.order.CreateOrderRequest;
import br.com.portaria.order.HolderRequest;
import br.com.portaria.order.OrderResponse;
import br.com.portaria.order.OrderService;
import br.com.portaria.ticket.QrCodeSigner;
import br.com.portaria.ticket.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /events/{publicId}/stats — contrato da secao 6 do SPEC.
 *
 * Os numeros sao construidos comprando e validando ingressos de verdade, nao
 * inserindo linhas prontas: e o unico jeito de o teste pegar divergencia entre
 * o que a compra grava e o que o painel soma.
 */
class StatsTest extends AbstractDatabaseTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private QrCodeSigner signer;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketBatchRepository batchRepository;

    @Autowired
    private TicketRepository ticketRepository;

    /** Evento com portoes abertos agora, para os ingressos poderem entrar. */
    private Event openEvent() {
        LocalDateTime now = LocalDateTime.now();
        return eventRepository.save(Event.builder()
                .name("Festa Universitaria 2026")
                .venue("Centro de Eventos, Orleans/SC")
                .gateOpensAt(now.minusHours(2))
                .startsAt(now.minusHours(1))
                .endsAt(now.plusHours(6))
                .status(EventStatus.PUBLISHED)
                .build());
    }

    private TicketBatch batch(Event event, String name, int priceCents, int total) {
        LocalDateTime now = LocalDateTime.now();
        return batchRepository.save(TicketBatch.builder()
                .event(event)
                .name(name)
                .priceCents(priceCents)
                .totalQuantity(total)
                .soldQuantity(0)
                .salesStart(now.minusDays(10))
                .salesEnd(now.plusDays(1))
                .build());
    }

    private OrderResponse buy(TicketBatch batch, int quantity, String document) {
        var holders = new java.util.ArrayList<HolderRequest>();
        for (int i = 0; i < quantity; i++) {
            holders.add(new HolderRequest("Titular " + i, null));
        }
        return orderService.create(new CreateOrderRequest(
                batch.getPublicId(), quantity,
                new BuyerRequest("Lucas", "lucas@exemplo.com", document),
                holders));
    }

    private void checkIn(UUID ticketPublicId) throws Exception {
        perform(post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckinRequest(signer.sign(ticketPublicId), "portaria-1"))))
                .andExpect(status().isOk());
    }

    @Test
    void deveSomarEmitidosPresentesEReceitaPorLote() throws Exception {
        Event event = openEvent();
        TicketBatch first = batch(event, "1o lote", 4500, 200);
        TicketBatch second = batch(event, "2o lote", 6000, 300);

        // 3 pagos no 1o lote (R$ 135,00) e 2 pagos no 2o (R$ 120,00)
        var paidFirst = orderService.pay(buy(first, 3, "11111111111").id());
        var paidSecond = orderService.pay(buy(second, 2, "22222222222").id());

        // 1 pedido apenas reservado: entra em sold, mas nao em emitidos nem receita
        buy(first, 1, "33333333333");

        // duas entradas: uma de cada lote
        checkIn(paidFirst.tickets().get(0).id());
        checkIn(paidSecond.tickets().get(0).id());

        perform(get("/api/v1/events/{id}/stats", event.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIssued").value(5))
                .andExpect(jsonPath("$.totalCheckedIn").value(2))
                .andExpect(jsonPath("$.totalRevenueCents").value(3 * 4500 + 2 * 6000))
                .andExpect(jsonPath("$.byBatch.length()").value(2))
                .andExpect(jsonPath("$.byBatch[0].name").value("1o lote"))
                .andExpect(jsonPath("$.byBatch[0].sold").value(4))      // 3 pagos + 1 reservado
                .andExpect(jsonPath("$.byBatch[0].checkedIn").value(1))
                .andExpect(jsonPath("$.byBatch[1].name").value("2o lote"))
                .andExpect(jsonPath("$.byBatch[1].sold").value(2))
                .andExpect(jsonPath("$.byBatch[1].checkedIn").value(1));
    }

    /** Pedido cancelado nao pode inflar emitidos nem receita. */
    @Test
    void naoDeveContarIngressosCanceladosComoEmitidos() throws Exception {
        Event event = openEvent();
        TicketBatch batch = batch(event, "1o lote", 4500, 200);

        orderService.pay(buy(batch, 2, "11111111111").id());
        var cancelled = orderService.pay(buy(batch, 2, "22222222222").id());
        orderService.cancel(cancelled.id());

        perform(get("/api/v1/events/{id}/stats", event.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIssued").value(2))
                .andExpect(jsonPath("$.totalCheckedIn").value(0))
                .andExpect(jsonPath("$.byBatch[0].checkedIn").value(0));
    }

    /** Lote sem nenhum ingresso ainda tem de aparecer, zerado — dai o LEFT JOIN. */
    @Test
    void deveListarLoteSemNenhumIngresso() throws Exception {
        Event event = openEvent();
        batch(event, "1o lote", 4500, 200);

        perform(get("/api/v1/events/{id}/stats", event.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIssued").value(0))
                .andExpect(jsonPath("$.totalCheckedIn").value(0))
                .andExpect(jsonPath("$.totalRevenueCents").value(0))
                .andExpect(jsonPath("$.byBatch.length()").value(1))
                .andExpect(jsonPath("$.byBatch[0].sold").value(0));
    }

    @Test
    void deveDevolverEstatisticasZeradasParaEventoSemLotes() throws Exception {
        Event event = openEvent();

        perform(get("/api/v1/events/{id}/stats", event.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenueCents").value(0))
                .andExpect(jsonPath("$.byBatch").isEmpty());
    }

    /** Nao pode vazar estatistica de um evento na consulta de outro. */
    @Test
    void naoDeveMisturarDadosDeOutroEvento() throws Exception {
        Event event = openEvent();
        TicketBatch mine = batch(event, "1o lote", 4500, 200);
        orderService.pay(buy(mine, 2, "11111111111").id());

        Event other = openEvent();
        TicketBatch theirs = batch(other, "lote do outro evento", 9900, 50);
        orderService.pay(buy(theirs, 3, "22222222222").id());

        perform(get("/api/v1/events/{id}/stats", event.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIssued").value(2))
                .andExpect(jsonPath("$.totalRevenueCents").value(2 * 4500))
                .andExpect(jsonPath("$.byBatch.length()").value(1));
    }

    @Test
    void deveDevolver404ParaEventoInexistente() throws Exception {
        perform(get("/api/v1/events/{id}/stats", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Evento nao encontrado"));
    }

    /** Identificador malformado nao pode virar 500 nem vazar stack trace. */
    @Test
    void deveDevolver400ParaIdentificadorMalformado() throws Exception {
        perform(get("/api/v1/events/{id}/stats", "nao-e-um-uuid"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Entrada com cara de injecao nao chega perto do banco: o conversor de
     * PathVariable rejeita antes, porque o tipo e UUID. E as consultas usam
     * parametros vinculados, nunca concatenacao.
     */
    @Test
    void deveTratarTentativaDeInjecaoComoIdentificadorInvalido() throws Exception {
        Event target = openEvent();
        batch(target, "1o lote", 4500, 200);

        perform(get("/api/v1/events/{id}/stats", "1' OR '1'='1"))
                .andExpect(status().isBadRequest());
        perform(get("/api/v1/events/{id}/stats", "'; DROP TABLE ticket; --"))
                .andExpect(status().isBadRequest());

        // o evento e o schema seguem intactos
        perform(get("/api/v1/events/{id}/stats", target.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byBatch.length()").value(1));
        assertThat(ticketRepository.count()).isZero();
    }
}
