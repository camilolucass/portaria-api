package br.com.portaria.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByPublicId(UUID publicId);

    List<Ticket> findByOrderIdOrderByIdAsc(Long orderId);

    /**
     * SPEC 7.3 — check-in atomico (problema P3).
     *
     * Mesmo principio da reserva de estoque: a condicao vai dentro do UPDATE e
     * quem decide e o numero de linhas afetadas. Entre N portarias tentando o
     * mesmo codigo ao mesmo tempo, exatamente uma recebe 1.
     *
     * clearAutomatically = true e obrigatorio: sem ele o findById seguinte
     * devolveria a versao em cache do contexto de persistencia, com o status
     * antigo, e a mensagem de erro sairia errada.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Ticket t
           SET t.status = :used, t.checkedInAt = :now, t.checkedInBy = :operator
         WHERE t.id = :id AND t.status = :issued
        """)
    int checkIn(@Param("id") Long id,
                @Param("now") LocalDateTime now,
                @Param("operator") String operator,
                @Param("used") TicketStatus used,
                @Param("issued") TicketStatus issued);
}
