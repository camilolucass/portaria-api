package br.com.portaria;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
