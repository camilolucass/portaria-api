package br.com.portaria.identity;

import br.com.portaria.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O actuator descreve o funcionamento interno da aplicacao. Nao e dado de
 * negocio, mas tambem nao e para qualquer conta autenticada: um comprador
 * logado nao precisa das metricas do servidor.
 *
 * O /health continua aberto porque o HEALTHCHECK do container o consulta sem
 * token.
 */
class ActuatorAccessTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("health e publico: o container consulta sem token")
    void healthEhPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("metrics sem token devolve 401")
    void metricsSemTokenDevolve401() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("comprador autenticado nao alcanca metrics")
    void compradorNaoAlcancaMetrics() throws Exception {
        perform(get("/actuator/metrics"), Role.BUYER)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("portaria autenticada nao alcanca metrics")
    void portariaNaoAlcancaMetrics() throws Exception {
        perform(get("/actuator/metrics"), Role.GATE)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("organizador alcanca metrics")
    void organizadorAlcancaMetrics() throws Exception {
        perform(get("/actuator/metrics"), Role.ORGANIZER)
                .andExpect(status().isOk());
    }
}
