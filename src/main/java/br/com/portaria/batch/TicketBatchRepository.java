package br.com.portaria.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketBatchRepository extends JpaRepository<TicketBatch, Long> {

    Optional<TicketBatch> findByPublicId(UUID publicId);

    List<TicketBatch> findByEventIdOrderBySalesStartAsc(Long eventId);

    /**
     * SPEC 7.1 — reserva de estoque sem oversell (P1).
     *
     * Ler o saldo e depois somar e o erro classico: entre a leitura e a escrita
     * cabe outra transacao. A soma acontece dentro do proprio UPDATE e a decisao
     * vem do numero de linhas afetadas — 0 significa que nao havia estoque.
     *
     * Sem lock explicito, sem @Version, sem retry: o banco ja serializa as
     * escritas concorrentes na mesma linha.
     */
    @Modifying
    @Query("""
        UPDATE TicketBatch b
           SET b.soldQuantity = b.soldQuantity + :quantity
         WHERE b.id = :batchId
           AND b.soldQuantity + :quantity <= b.totalQuantity
        """)
    int reserve(@Param("batchId") Long batchId, @Param("quantity") int quantity);

    /** Devolve estoque ao lote (RN-06 e RN-08). Mesmo principio: condicao dentro do UPDATE. */
    @Modifying
    @Query("""
        UPDATE TicketBatch b
           SET b.soldQuantity = b.soldQuantity - :quantity
         WHERE b.id = :batchId AND b.soldQuantity >= :quantity
        """)
    int release(@Param("batchId") Long batchId, @Param("quantity") int quantity);
}
