package br.com.portaria.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPublicId(UUID publicId);

    /**
     * Ordenado por id de proposito: o job roda em todas as instancias, e duas
     * instancias percorrendo os mesmos pedidos em ordens diferentes tomariam os
     * locks de linha em ordem cruzada, abrindo espaco para deadlock.
     */
    List<PurchaseOrder> findByStatusAndExpiresAtBeforeOrderByIdAsc(OrderStatus status,
                                                                   LocalDateTime moment);

    /**
     * Reivindica o pedido para expiracao. Mesmo padrao da reserva de estoque
     * (SPEC 7.1) e do check-in (7.3): a condicao vai dentro do UPDATE e quem
     * decide e o numero de linhas afetadas.
     *
     * Com varias instancias da aplicacao rodando o job ao mesmo tempo, apenas
     * uma afeta a linha; as demais recebem 0 e nao devolvem estoque. Sem isso o
     * estoque volta uma vez por instancia e o lote passa a vender vagas que nao
     * existem.
     */
    @Modifying
    @Query("""
        UPDATE PurchaseOrder o
           SET o.status = :expired
         WHERE o.id = :id AND o.status = :pending
        """)
    int markExpired(@Param("id") Long id,
                    @Param("expired") OrderStatus expired,
                    @Param("pending") OrderStatus pending);
}
