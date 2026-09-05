package br.com.portaria.event;

import br.com.portaria.AbstractIntegrationTest;
import br.com.portaria.identity.AppUser;
import br.com.portaria.identity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sem teto configurado, o padrao do Spring Data e 2000 itens por requisicao, e
 * um ?size=2000 repetido pesa no banco sem custo nenhum para quem pede.
 *
 * O teste olha o tamanho da pagina que a API devolve, e nao a propriedade do
 * arquivo de configuracao: e o comportamento que importa.
 */
@Transactional
class PageSizeLimitTest extends AbstractIntegrationTest {

    private static final int MAXIMO = 100;
    private static final int PADRAO = 20;

    private AppUser organizer;

    @BeforeEach
    void createOrganizer() {
        organizer = createUser("organizadora@exemplo.com", Role.ORGANIZER);
    }

    @Test
    @DisplayName("size acima do teto e reduzido ao maximo")
    void sizeAcimaDoTetoEhReduzido() throws Exception {
        performAs(organizer, get("/api/v1/events?size=5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(MAXIMO));
    }

    @Test
    @DisplayName("size dentro do teto e respeitado")
    void sizeDentroDoTetoEhRespeitado() throws Exception {
        performAs(organizer, get("/api/v1/events?size=30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(30));
    }

    @Test
    @DisplayName("sem size, vale o padrao")
    void semSizeValeOPadrao() throws Exception {
        performAs(organizer, get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(PADRAO));
    }
}
