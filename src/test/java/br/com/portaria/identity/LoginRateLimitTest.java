package br.com.portaria.identity;

import br.com.portaria.AbstractDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Freio de forca bruta no login.
 *
 * Sem ele da para varrer senhas sem limite — e cada tentativa custa um BCrypt
 * completo de CPU nossa, o que torna a varredura barata para quem ataca e cara
 * para quem hospeda.
 */
class LoginRateLimitTest extends AbstractDatabaseTest {

    /**
     * Um e-mail por teste. O freio guarda contagem em memoria num bean
     * singleton, entao um e-mail fixo faria o bloqueio de um teste derrubar o
     * seguinte — o truncate do banco nao limpa mapa em memoria.
     */
    private String email;
    private static final String PASSWORD = "senha-de-teste-123";
    private static final int MAX_PER_ACCOUNT = 3;

    /** Limites pequenos para o teste nao precisar de dezenas de requisicoes. */
    @DynamicPropertySource
    static void rateLimitProperties(DynamicPropertyRegistry registry) {
        registry.add("app.login.max-attempts-per-account", () -> MAX_PER_ACCOUNT);
        // o limite por endereco fica praticamente desligado aqui: todos os
        // testes vem do mesmo 127.0.0.1 e um ao lado do outro estourariam a
        // contagem. Essa regra tem teste proprio em LoginAttemptServiceTest
        registry.add("app.login.max-attempts-per-address", () -> 10_000);
        registry.add("app.login.block-duration", () -> "2s");
    }

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createUser(TestInfo info) {
        email = info.getTestMethod().orElseThrow().getName().toLowerCase() + "@exemplo.com";
        userRepository.save(AppUser.builder()
                .name("Organizadora")
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .roles(EnumSet.of(Role.ORGANIZER))
                .build());
    }

    private org.springframework.test.web.servlet.ResultActions attempt(String email, String password)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest(email, password))));
    }

    @Test
    void apuraTentativasEBloqueiaDepoisDoLimite() throws Exception {
        for (int i = 0; i < MAX_PER_ACCOUNT; i++) {
            attempt(email, "senha-errada").andExpect(status().isUnauthorized());
        }

        attempt(email, "senha-errada")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Muitas tentativas"))
                .andExpect(header().exists("Retry-After"));
    }

    /**
     * O ponto do freio: depois de bloqueado, nem a senha CORRETA passa. Se
     * passasse, bastaria continuar tentando ate acertar.
     */
    @Test
    void depoisDeBloqueadoNemASenhaCorretaEntra() throws Exception {
        for (int i = 0; i < MAX_PER_ACCOUNT; i++) {
            attempt(email, "senha-errada").andExpect(status().isUnauthorized());
        }

        attempt(email, PASSWORD).andExpect(status().isTooManyRequests());
    }

    /** Acertar antes do limite zera a contagem: usuario que erra e depois lembra. */
    @Test
    void acertoAntesDoLimiteZeraAContagem() throws Exception {
        attempt(email, "senha-errada").andExpect(status().isUnauthorized());
        attempt(email, "senha-errada").andExpect(status().isUnauthorized());

        attempt(email, PASSWORD).andExpect(status().isOk());

        // a contagem voltou a zero, entao ainda cabem MAX tentativas
        for (int i = 0; i < MAX_PER_ACCOUNT; i++) {
            attempt(email, "senha-errada").andExpect(status().isUnauthorized());
        }
        attempt(email, "senha-errada").andExpect(status().isTooManyRequests());
    }

    /**
     * Conta inexistente tambem e bloqueada. Se so as existentes travassem, o
     * proprio bloqueio diria quais e-mails tem conta — o oraculo que a mensagem
     * generica de credenciais existe para evitar.
     */
    @Test
    void contaInexistenteTambemEBloqueada() throws Exception {
        for (int i = 0; i < MAX_PER_ACCOUNT; i++) {
            attempt("inexistente-" + email, "chute").andExpect(status().isUnauthorized());
        }

        attempt("inexistente-" + email, "chute").andExpect(status().isTooManyRequests());
    }

    /** Bloquear uma conta nao pode derrubar as outras do mesmo endereco. */
    @Test
    void bloqueioDeUmaContaNaoAtingeOutra() throws Exception {
        for (int i = 0; i < MAX_PER_ACCOUNT + 1; i++) {
            attempt(email, "senha-errada");
        }

        userRepository.save(AppUser.builder()
                .name("Outra")
                .email("outra@exemplo.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .roles(EnumSet.of(Role.BUYER))
                .build());

        attempt("outra@exemplo.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void oCorpoDoBloqueioInformaQuandoTentarDeNovo() throws Exception {
        for (int i = 0; i < MAX_PER_ACCOUNT + 1; i++) {
            attempt(email, "senha-errada");
        }

        String body = attempt(email, "senha-errada")
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).get("retryAfterSeconds").asLong()).isPositive();
    }
}
