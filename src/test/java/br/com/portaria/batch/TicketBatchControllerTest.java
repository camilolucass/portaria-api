package br.com.portaria.batch;

import br.com.portaria.AbstractIntegrationTest;
import br.com.portaria.event.CreateEventRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class TicketBatchControllerTest extends AbstractIntegrationTest {

    private static final LocalDateTime GATE  = LocalDateTime.of(2026, 10, 10, 21, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 10, 22, 0);
    private static final LocalDateTime END   = LocalDateTime.of(2026, 10, 11, 4, 0);

    @Test
    void deveCriarLoteComAvailableQuantityCheio() throws Exception {
        String eventId = createEvent();
        var request = new CreateBatchRequest("1o lote", 4500, 200,
                LocalDateTime.of(2026, 9, 10, 0, 0), LocalDateTime.of(2026, 10, 5, 23, 59));

        mockMvc.perform(post("/api/v1/events/{id}/batches", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.priceCents").value(4500))
                .andExpect(jsonPath("$.soldQuantity").value(0))
                .andExpect(jsonPath("$.availableQuantity").value(200));
    }

    @Test
    void deveRecusarPrecoZeroCom400() throws Exception {
        String eventId = createEvent();
        var request = new CreateBatchRequest("1o lote", 0, 200,
                LocalDateTime.of(2026, 9, 10, 0, 0), LocalDateTime.of(2026, 10, 5, 23, 59));

        mockMvc.perform(post("/api/v1/events/{id}/batches", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.priceCents").isNotEmpty());
    }

    /** RN-02: sales_end nao pode ultrapassar event.starts_at. */
    @Test
    void deveRecusarVendaTerminandoDepoisDoInicioDoEventoCom422() throws Exception {
        String eventId = createEvent();
        var request = new CreateBatchRequest("1o lote", 4500, 200,
                LocalDateTime.of(2026, 9, 10, 0, 0), START.plusMinutes(1));

        mockMvc.perform(post("/api/v1/events/{id}/batches", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Periodo invalido"));
    }

    @Test
    void deveRecusarFimDeVendaAntesDoInicioCom422() throws Exception {
        String eventId = createEvent();
        var request = new CreateBatchRequest("1o lote", 4500, 200,
                LocalDateTime.of(2026, 10, 5, 0, 0), LocalDateTime.of(2026, 9, 10, 0, 0));

        mockMvc.perform(post("/api/v1/events/{id}/batches", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Periodo invalido"));
    }

    @Test
    void deveDevolver404AoCriarLoteEmEventoInexistente() throws Exception {
        var request = new CreateBatchRequest("1o lote", 4500, 200,
                LocalDateTime.of(2026, 9, 10, 0, 0), LocalDateTime.of(2026, 10, 5, 23, 59));

        mockMvc.perform(post("/api/v1/events/{id}/batches", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Evento nao encontrado"));
    }

    @Test
    void deveListarLotesDoEventoOrdenadosPeloInicioDaVenda() throws Exception {
        String eventId = createEvent();
        createBatch(eventId, "2o lote", 6000, 100,
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 5, 23, 59));
        createBatch(eventId, "1o lote", 4500, 200,
                LocalDateTime.of(2026, 9, 10, 0, 0), LocalDateTime.of(2026, 9, 30, 23, 59));

        mockMvc.perform(get("/api/v1/events/{id}/batches", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("1o lote"))
                .andExpect(jsonPath("$[0].availableQuantity").value(200))
                .andExpect(jsonPath("$[1].name").value("2o lote"))
                .andExpect(jsonPath("$[1].availableQuantity").value(100));
    }

    private String createEvent() throws Exception {
        var event = new CreateEventRequest("Festa Universitaria 2026", "Open bar",
                "Centro de Eventos, Orleans/SC", GATE, START, END);
        String body = mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(event)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void createBatch(String eventId, String name, int priceCents, int total,
                             LocalDateTime salesStart, LocalDateTime salesEnd) throws Exception {
        var request = new CreateBatchRequest(name, priceCents, total, salesStart, salesEnd);
        mockMvc.perform(post("/api/v1/events/{id}/batches", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated());
    }
}
