package br.com.portaria.batch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketBatchRepository extends JpaRepository<TicketBatch, Long> {

    Optional<TicketBatch> findByPublicId(UUID publicId);

    List<TicketBatch> findByEventIdOrderBySalesStartAsc(Long eventId);
}
