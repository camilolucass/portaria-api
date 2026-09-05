package br.com.portaria.order;

import br.com.portaria.batch.TicketBatch;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.batch.TicketBatchService;
import br.com.portaria.identity.AppUser;
import br.com.portaria.identity.CurrentUserService;
import br.com.portaria.shared.exception.InvalidOrderStatusException;
import br.com.portaria.shared.exception.OrderNotFoundException;
import br.com.portaria.shared.exception.SoldOutException;
import br.com.portaria.ticket.Ticket;
import br.com.portaria.ticket.TicketRepository;
import br.com.portaria.ticket.TicketStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final BuyerRepository buyerRepository;
    private final TicketRepository ticketRepository;
    private final TicketBatchRepository batchRepository;
    private final TicketBatchService batchService;
    private final CurrentUserService currentUser;
    private final int expirationMinutes;

    public OrderService(OrderRepository orderRepository,
                        BuyerRepository buyerRepository,
                        TicketRepository ticketRepository,
                        TicketBatchRepository batchRepository,
                        TicketBatchService batchService,
                        CurrentUserService currentUser,
                        @Value("${app.order.expiration-minutes}") int expirationMinutes) {
        this.orderRepository = orderRepository;
        this.buyerRepository = buyerRepository;
        this.ticketRepository = ticketRepository;
        this.batchRepository = batchRepository;
        this.batchService = batchService;
        this.currentUser = currentUser;
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * RN-03, RN-04 e RN-05. A reserva de estoque acontece antes de qualquer
     * escrita do pedido: se o UPDATE condicional nao afetar linha nenhuma, o
     * lote acabou e nada mais e gravado.
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        AppUser account = currentUser.require();
        TicketBatch batch = batchService.findEntity(request.batchId());
        batchService.assertOnSale(batch, LocalDateTime.now());          // RN-01, RN-02

        if (batchRepository.reserve(batch.getId(), request.quantity()) == 0) {   // SPEC 7.1
            throw new SoldOutException(
                    "O lote \"%s\" nao tem %d ingresso(s) disponivel(is)."
                            .formatted(batch.getName(), request.quantity()));
        }

        Buyer buyer = findOrCreateBuyer(request.buyer());

        PurchaseOrder order = PurchaseOrder.builder()
                .user(account)
                .buyer(buyer)
                .batch(batch)
                .quantity(request.quantity())
                .totalCents(batch.getPriceCents() * request.quantity())
                .status(OrderStatus.PENDING)                            // RN-05
                .expiresAt(LocalDateTime.now().plusMinutes(expirationMinutes))
                .build();

        request.holders().forEach(holder -> order.addHolder(
                OrderHolder.builder().name(holder.name()).document(holder.document()).build()));

        orderRepository.save(order);
        return OrderResponse.of(order, List.of());
    }

    /**
     * RN-07 — os ingressos nascem aqui e so aqui, um por titular informado na
     * criacao do pedido.
     *
     * Fase 1 apenas: na Fase 3 esta rota some e quem dispara a transicao e o
     * webhook do gateway.
     */
    @Transactional
    public OrderResponse pay(UUID publicId) {
        PurchaseOrder order = findEntity(publicId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStatusException(
                    "Apenas um pedido PENDING pode ser pago. Situacao atual: %s."
                            .formatted(order.getStatus()));
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());

        List<Ticket> tickets = order.getHolders().stream()
                .map(holder -> Ticket.builder()
                        .order(order)
                        .batch(order.getBatch())
                        .holderName(holder.getName())
                        .holderDocument(holder.getDocument())
                        .status(TicketStatus.ISSUED)
                        .build())
                .toList();

        ticketRepository.saveAll(tickets);
        return OrderResponse.of(order, tickets);
    }

    @Transactional(readOnly = true)
    public OrderResponse findByPublicId(UUID publicId) {
        PurchaseOrder order = findEntity(publicId);
        return OrderResponse.of(order, ticketRepository.findByOrderIdOrderByIdAsc(order.getId()));
    }

    /**
     * RN-08 — cancelar um pedido PAID cancela os ingressos ainda nao usados e
     * devolve ao lote apenas o estoque correspondente a eles. O que ja entrou
     * na festa nao volta para a prateleira.
     */
    @Transactional
    public OrderResponse cancel(UUID publicId) {
        PurchaseOrder order = findEntity(publicId);

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PAID) {
            throw new InvalidOrderStatusException(
                    "Apenas um pedido PENDING ou PAID pode ser cancelado. Situacao atual: %s."
                            .formatted(order.getStatus()));
        }

        List<Ticket> tickets = ticketRepository.findByOrderIdOrderByIdAsc(order.getId());
        List<Ticket> cancellable = tickets.stream()
                .filter(ticket -> ticket.getStatus() == TicketStatus.ISSUED)
                .toList();

        cancellable.forEach(ticket -> ticket.setStatus(TicketStatus.CANCELLED));

        int toRelease = tickets.isEmpty() ? order.getQuantity() : cancellable.size();
        if (toRelease > 0) {
            batchRepository.release(order.getBatch().getId(), toRelease);
        }

        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.of(order, ticketRepository.findByOrderIdOrderByIdAsc(order.getId()));
    }

    /**
     * Pedido da conta autenticada.
     *
     * Pedido de outra conta devolve 404, e nao 403: um 403 confirmaria que
     * aquele identificador existe. Antes da Fase 2, quem descobrisse o publicId
     * de um pedido lia os ingressos e o codigo do QR de outra pessoa.
     */
    @Transactional(readOnly = true)
    public PurchaseOrder findEntity(UUID publicId) {
        return orderRepository.findByPublicIdAndUserId(publicId, currentUser.require().getId())
                .orElseThrow(() -> new OrderNotFoundException(publicId));
    }

    /** O mesmo CPF comprando de novo reaproveita o comprador (buyer_document_uk). */
    private Buyer findOrCreateBuyer(BuyerRequest request) {
        return buyerRepository.findByDocument(request.document())
                .orElseGet(() -> buyerRepository.save(Buyer.builder()
                        .name(request.name())
                        .email(request.email())
                        .document(request.document())
                        .build()));
    }
}
