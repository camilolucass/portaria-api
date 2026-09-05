package br.com.portaria.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    /**
     * SPEC — idempotencia do webhook (P4).
     *
     * Mesmo padrao dos P1 e P3: a condicao vai dentro da escrita e quem decide e
     * o numero de linhas afetadas. ON CONFLICT DO NOTHING devolve 1 para a
     * primeira notificacao e 0 para toda repeticao, inclusive quando duas
     * chegam no mesmo milissegundo — o banco serializa no indice unico.
     *
     * Nao e "verificar se existe e depois inserir": entre a verificacao e a
     * insercao cabe outra requisicao, que e exatamente o problema.
     */
    @Modifying
    @Query(value = """
        INSERT INTO payment_event (external_id, order_id, status)
             VALUES (:externalId, :orderId, :status)
        ON CONFLICT (external_id) DO NOTHING
        """, nativeQuery = true)
    int recordIfNew(@Param("externalId") String externalId,
                    @Param("orderId") Long orderId,
                    @Param("status") String status);
}
