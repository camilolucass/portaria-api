package br.com.portaria.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByPublicId(UUID publicId);

    /** Escopo do organizador: os proprios eventos, em qualquer situacao. */
    Page<Event> findByOrganizerId(Long organizerId, Pageable pageable);

    /** Escopo de quem compra: so o que esta a venda, de qualquer organizador. */
    Page<Event> findByStatus(EventStatus status, Pageable pageable);
}
