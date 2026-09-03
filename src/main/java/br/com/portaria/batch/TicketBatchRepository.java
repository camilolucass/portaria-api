package br.com.portaria.batch;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketBatchRepository extends JpaRepository<TicketBatch, Long> {
}
