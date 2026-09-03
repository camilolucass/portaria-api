package br.com.portaria.event;

import br.com.portaria.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class EventControllerTest extends AbstractIntegrationTest {

    private static final LocalDateTime GATE  = LocalDateTime.of(2026, 10, 10, 21, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 10, 22, 0);
    private static final LocalDateTime END   = LocalDateTime.of(2026, 10, 11, 4, 0);

    private CreateEventRequest validRequest() {
        return new CreateEventRequest("Festa Universitaria 2026", "Open bar",
                "Centro de Eventos, Orleans/SC", GATE, START, END);
    }

    @Test
    void deveCriarEventoEmDraftEDevolver201() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.name").value("Festa Universitaria 2026"));
    }

    @Test
    void deveRecusarEventoSemNomeCom400() throws Exception {
        var invalid = new CreateEventRequest("  ", null, "Centro de Eventos", GATE, START, END);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisicao invalida"))
                .andExpect(jsonPath("$.fields.name").isNotEmpty());
    }

    @Test
    void deveRecusarEventoQueTerminaAntesDeComecarCom422() throws Exception {
        var invalid = new CreateEventRequest("Festa", null, "Centro", GATE, START, START.minusHours(1));

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Periodo invalido"));
    }

    @Test
    void deveRecusarPortaoAbrindoDepoisDoInicioCom422() throws Exception {
        var invalid = new CreateEventRequest("Festa", null, "Centro", START.plusMinutes(1), START, END);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Periodo invalido"));
    }

    @Test
    void devePublicarEventoEmDraft() throws Exception {
        String id = createEvent();

        mockMvc.perform(post("/api/v1/events/{id}/publish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void deveRecusarPublicacaoRepetidaCom409() throws Exception {
        String id = createEvent();
        mockMvc.perform(post("/api/v1/events/{id}/publish", id)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/events/{id}/publish", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Situacao do evento nao permite a operacao"));
    }

    @Test
    void deveDevolver404ParaEventoInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/events/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Evento nao encontrado"));
    }

    @Test
    void deveBuscarEventoPeloPublicId() throws Exception {
        String id = createEvent();

        mockMvc.perform(get("/api/v1/events/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.venue").value("Centro de Eventos, Orleans/SC"));
    }

    @Test
    void deveListarEventosPaginado() throws Exception {
        createEvent();

        mockMvc.perform(get("/api/v1/events").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.size").value(5));
    }

    private String createEvent() throws Exception {
        String body = mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }
}
