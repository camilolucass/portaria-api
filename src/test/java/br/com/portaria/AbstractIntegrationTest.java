package br.com.portaria;

import br.com.portaria.identity.AppUser;
import br.com.portaria.identity.AppUserRepository;
import br.com.portaria.identity.Role;
import br.com.portaria.identity.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

    @Autowired
    protected AppUserRepository userRepository;

    @Autowired
    protected JwtDecoder jwtDecoder;

    @org.junit.jupiter.api.AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

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
     * Cria uma conta real no banco. A autorizacao da Etapa 2 depende de
     * identidade — dono do evento, dono do pedido, portaria vinculada — entao o
     * usuario precisa existir de fato.
     */
    protected AppUser createUser(String email, Role... roles) {
        return userRepository.save(AppUser.builder()
                .name("Usuario " + email)
                .email(email)
                .passwordHash("{noop}nao-usado-em-teste")
                .roles(roles.length == 0
                        ? java.util.EnumSet.noneOf(Role.class)
                        : java.util.EnumSet.copyOf(java.util.Set.of(roles)))
                .build());
    }

    /** Executa a requisicao como uma conta especifica. */
    protected ResultActions performAs(AppUser user, MockHttpServletRequestBuilder builder)
            throws Exception {
        return mockMvc.perform(builder.header("Authorization", "Bearer " + tokenFor(user)));
    }

    protected String tokenFor(AppUser user) {
        return tokenService.issueFor(user).token();
    }

    /**
     * Coloca a conta no SecurityContext da thread atual, do mesmo jeito que o
     * filtro faria: decodificando um token de verdade. Necessario nos testes que
     * chamam services diretamente, sem passar por HTTP.
     *
     * O contexto e ThreadLocal — nos testes de concorrencia isto precisa ser
     * chamado dentro de cada thread.
     */
    protected void authenticateAs(AppUser user) {
        var jwt = jwtDecoder.decode(tokenFor(user));
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .map(org.springframework.security.core.GrantedAuthority.class::cast)
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }

    /**
     * Token assinado pelo TokenService real, para uma conta que nao existe no
     * banco. Serve aos testes de autenticacao: o resource server valida a
     * assinatura sem consultar o app_user, entao isto exercita exatamente o
     * filtro. Rotas que exigem identidade recusam este token.
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
