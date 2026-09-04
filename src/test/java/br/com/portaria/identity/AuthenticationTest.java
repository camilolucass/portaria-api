package br.com.portaria.identity;

import br.com.portaria.AbstractDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fase 2, Etapa 1 — quem entra, como entra, e o que acontece com quem nao entra. */
class AuthenticationTest extends AbstractDatabaseTest {

    private static final String EMAIL = "organizadora@exemplo.com";
    private static final String PASSWORD = "senha-de-teste-123";

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AppUser createUser() {
        return userRepository.save(AppUser.builder()
                .name("Organizadora Demo")
                .email(EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .roles(EnumSet.of(Role.ORGANIZER))
                .build());
    }

    private String loginBody(String email, String password) throws Exception {
        return json(new LoginRequest(email, password));
    }

    // login -------------------------------------------------------------------

    @Test
    void deveEmitirTokenParaCredenciaisCorretas() throws Exception {
        createUser();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.roles[0]").value("ORGANIZER"));
    }

    /** O e-mail nao pode ser sensivel a maiusculas: senao viram duas contas. */
    @Test
    void deveAceitarEmailEmMaiusculasNoLogin() throws Exception {
        createUser();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("  ORGANIZADORA@Exemplo.COM  ", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    /**
     * Senha errada e e-mail inexistente devolvem exatamente a mesma resposta.
     * Se diferissem, bastaria varrer uma lista de e-mails para descobrir quais
     * tem conta neste sistema.
     */
    @Test
    void senhaErradaEEmailInexistenteDevemSerIndistinguiveis() throws Exception {
        createUser();

        String senhaErrada = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, "senha-errada")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String emailInexistente = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ninguem@exemplo.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        var comSenhaErrada = objectMapper.readTree(senhaErrada);
        var comEmailInexistente = objectMapper.readTree(emailInexistente);

        assertThat(comSenhaErrada.get("title").asString())
                .isEqualTo(comEmailInexistente.get("title").asString())
                .isEqualTo("Credenciais invalidas");
        assertThat(comSenhaErrada.get("detail").asString())
                .isEqualTo(comEmailInexistente.get("detail").asString());
    }

    @Test
    void contaDesabilitadaNaoRecebeToken() throws Exception {
        AppUser user = createUser();
        user.setEnabled(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Credenciais invalidas"));
    }

    @Test
    void deveRecusarLoginSemCamposCom400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisicao invalida"));
    }

    @Test
    void aSenhaNuncaPodeVoltarNaResposta() throws Exception {
        createUser();

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(PASSWORD).doesNotContain("passwordHash").doesNotContain("$2a$");
    }

    // protecao das rotas ------------------------------------------------------

    @Test
    void semTokenAsRotasDeNegocioDevolvem401EmProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Nao autenticado"))
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/events/{id}/stats", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenComAssinaturaAlteradaDevolve401() throws Exception {
        String token = tokenFor(Role.ORGANIZER);
        String adulterado = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        mockMvc.perform(get("/api/v1/events").header("Authorization", "Bearer " + adulterado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Nao autenticado"));
    }

    @Test
    void tokenAssinadoComOutraChaveDevolve401() throws Exception {
        // um atacante que conheca o formato mas nao a chave nao entra
        String forjado = "eyJhbGciOiJIUzI1NiJ9"
                + ".eyJzdWIiOiJhdGFjYW50ZSIsInJvbGVzIjpbIk9SR0FOSVpFUiJdfQ"
                + ".assinatura-invalida";

        mockMvc.perform(get("/api/v1/events").header("Authorization", "Bearer " + forjado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void comTokenValidoAsRotasDeNegocioRespondem() throws Exception {
        perform(get("/api/v1/events"), Role.ORGANIZER)
                .andExpect(status().isOk());
    }

    // rotas publicas ----------------------------------------------------------

    @Test
    void saudeEDocumentacaoSeguemPublicas() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    /** Metricas expoem uso interno: nao entram na lista de rotas abertas. */
    @Test
    void metricasNaoPodemSerPublicas() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }
}
