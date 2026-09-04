package br.com.portaria.shared;

import br.com.portaria.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O SPEC pede @Operation em toda rota. Um Swagger que nao abre, ou que perde uma
 * rota, e pior que nenhum: passa a impressao de documentacao completa.
 */
@Transactional
class OpenApiDocsTest extends AbstractIntegrationTest {

    @Test
    void deveExporTodasAsRotasDaFase1() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Portaria API"))
                .andExpect(jsonPath("$.paths['/api/v1/events'].post.summary").value("Cria um evento"))
                .andExpect(jsonPath("$.paths['/api/v1/events/{publicId}/publish'].post.summary")
                        .value("Publica o evento"))
                .andExpect(jsonPath("$.paths['/api/v1/events/{eventPublicId}/batches'].post.summary")
                        .value("Cria um lote"))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.summary").value("Cria um pedido"))
                .andExpect(jsonPath("$.paths['/api/v1/orders/{publicId}/pay'].post.summary")
                        .value("Confirma o pagamento"))
                .andExpect(jsonPath("$.paths['/api/v1/tickets/{publicId}/qr'].get.summary")
                        .value("Renderiza o QR Code"))
                .andExpect(jsonPath("$.paths['/api/v1/checkins'].post.summary")
                        .value("Valida a entrada"));
    }

    /**
     * O SPEC pede @Operation em TODA rota. Comparar a contagem de summaries com a
     * de operacoes faz uma rota nova sem anotacao quebrar o build, em vez de
     * passar despercebida.
     */
    @Test
    void nenhumaRotaPodeFicarSemSummary() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var paths = objectMapper.readTree(body).get("paths");
        int operations = 0;
        int withSummary = 0;

        for (var path : paths) {
            for (var operation : path) {
                operations++;
                if (operation.has("summary") && !operation.get("summary").asString().isBlank()) {
                    withSummary++;
                }
            }
        }

        org.assertj.core.api.Assertions.assertThat(operations).isPositive();
        org.assertj.core.api.Assertions.assertThat(withSummary).isEqualTo(operations);
    }

    @Test
    void deveExporOActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
