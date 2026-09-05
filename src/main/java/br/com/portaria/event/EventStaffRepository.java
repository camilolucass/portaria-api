package br.com.portaria.event;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventStaffRepository extends JpaRepository<EventStaff, EventStaff.EventStaffId> {

    boolean existsByEventIdAndUserId(Long eventId, Long userId);
}
