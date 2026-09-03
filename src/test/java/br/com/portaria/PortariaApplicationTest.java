package br.com.portaria;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Etapa 1: o contexto so sobe se a migration V1 e o mapeamento das entidades
 * baterem, porque ddl-auto=validate roda na inicializacao do EntityManagerFactory.
 * Postgres real via Testcontainers -- H2 nao reproduz o comportamento do banco.
 */
@SpringBootTest
@Testcontainers
class PortariaApplicationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // schema validado contra o Postgres real
    }
}
