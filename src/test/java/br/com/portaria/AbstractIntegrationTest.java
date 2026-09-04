package br.com.portaria;

import br.com.portaria.identity.AppUser;
import br.com.portaria.identity.Role;
import br.com.portaria.identity.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;

/**
 * Base de todo teste de integracao. Postgres real via Testcontainers — H2 e
 * proibido pelo SPEC, inclusive em teste.
 *
 * O container e singleton (start no bloco estatico, sem @Testcontainers) para
 * ser reaproveitado por todas as classes de teste da mesma JVM; subir um
 * Postgres por classe custaria dezenas de segundos a toa.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    /** Segredos de teste, fixos e obviamente falsos. Em producao vem do ambiente. */
    protected static final String QR_SECRET = "segredo-de-teste-com-mais-de-32-caracteres";
    protected static final String JWT_SECRET = "segredo-jwt-de-teste-com-mais-de-32-caracteres";

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.qr.secret", () -> QR_SECRET);
        registry.add("app.jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TokenService tokenService;

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /**
     * Executa a requisicao autenticada. Use este metodo por padrao; chamar
     * mockMvc.perform diretamente significa "sem token", que e o que os testes
     * de negacao precisam.
     */
    protected ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return perform(builder, Role.values());
    }

    protected ResultActions perform(MockHttpServletRequestBuilder builder, Role... roles)
            throws Exception {
        return mockMvc.perform(builder.header("Authorization", "Bearer " + tokenFor(roles)));
    }

    /**
     * Token assinado de verdade, pelo mesmo TokenService da aplicacao — assim o
     * teste passa pelo filtro real em vez de simular a autenticacao.
     *
     * O usuario nao precisa existir no banco: o resource server valida a
     * assinatura e le os papeis da claim, sem consultar o app_user a cada
     * requisicao. Onde a identidade importar de fato (dono do evento, portaria
     * vinculada), a Etapa 2 usa usuarios reais.
     */
    protected String tokenFor(Role... roles) {
        var user = AppUser.builder()
                .name("Usuario de teste")
                .email("teste@exemplo.com")
                .passwordHash("nao-usado")
                .roles(java.util.Set.of(roles))
                .build();
        return tokenService.issueFor(user).token();
    }
}
