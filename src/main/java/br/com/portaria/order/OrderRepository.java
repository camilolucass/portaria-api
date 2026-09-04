package br.com.portaria.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPublicId(UUID publicId);

    List<PurchaseOrder> findByStatusAndExpiresAtBefore(OrderStatus status, LocalDateTime moment);
}
