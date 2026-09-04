package br.com.portaria.stats;

import br.com.portaria.batch.TicketBatch;
import br.com.portaria.order.OrderStatus;
import br.com.portaria.ticket.TicketStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Consultas agregadas do painel. Duas queries resolvem o endpoint inteiro —
 * nada de carregar ingressos em memoria para contar em Java, que seria O(n) de
 * objetos por request.
 *
 * Todo parametro e vinculado (:eventId, :used, :paid). Nenhuma parte da consulta
 * e montada por concatenacao de string, entao nao ha superficie para injecao.
 */
public interface StatsRepository extends Repository<TicketBatch, Long> {

    /**
     * Uma linha por lote do evento, inclusive lotes sem nenhum ingresso — dai o
     * LEFT JOIN. Ingressos CANCELLED nao contam como emitidos: o comprador
     * desistiu, aquilo nao e publico esperado na porta.
     */
    @Query("""
        SELECT new br.com.portaria.stats.BatchStatsProjection(
                   b.name,
                   b.soldQuantity,
                   COUNT(t.id),
                   COALESCE(SUM(CASE WHEN t.status = :used THEN 1L ELSE 0L END), 0L))
          FROM TicketBatch b
          LEFT JOIN Ticket t ON t.batch = b AND t.status <> :cancelled
         WHERE b.event.id = :eventId
         GROUP BY b.id, b.name, b.soldQuantity, b.salesStart
         ORDER BY b.salesStart ASC
        """)
    List<BatchStatsProjection> statsByBatch(@Param("eventId") Long eventId,
                                            @Param("used") TicketStatus used,
                                            @Param("cancelled") TicketStatus cancelled);

    /**
     * Receita = soma dos pedidos efetivamente pagos. Pedido PENDING reserva
     * estoque mas nao entrou dinheiro; EXPIRED e CANCELLED muito menos.
     *
     * O SUM sai como Long por decisao explicita: somar total_cents em int
     * estouraria sem aviso.
     */
    @Query("""
        SELECT COALESCE(SUM(o.totalCents), 0L)
          FROM PurchaseOrder o
         WHERE o.batch.event.id = :eventId
           AND o.status = :paid
        """)
    long revenueCents(@Param("eventId") Long eventId, @Param("paid") OrderStatus paid);
}
