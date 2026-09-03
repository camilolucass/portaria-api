package br.com.portaria;

import org.junit.jupiter.api.Test;

/**
 * Etapa 1: o contexto so sobe se a migration V1 e o mapeamento das entidades
 * baterem, porque ddl-auto=validate roda na inicializacao do EntityManagerFactory.
 */
class PortariaApplicationTest extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // schema validado contra o Postgres real
    }
}
