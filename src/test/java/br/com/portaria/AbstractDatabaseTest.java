package br.com.portaria;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base para os testes que NAO podem rodar dentro de uma transacao de teste:
 * concorrencia (as threads precisam de commits reais) e tudo que checa efeito
 * de UPDATE em lote, que ficaria mascarado pelo cache do contexto de persistencia.
 *
 * Como nao ha rollback automatico, a limpeza e explicita.
 */
public abstract class AbstractDatabaseTest extends AbstractIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE payment_event, ticket, order_holder, purchase_order, ticket_batch, event, buyer,
                         user_role, app_user
                RESTART IDENTITY CASCADE
                """);
    }
}
