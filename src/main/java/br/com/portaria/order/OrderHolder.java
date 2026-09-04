package br.com.portaria.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Titular informado na criacao do pedido. Existe porque a RN-07 so permite
 * gerar o ingresso na transicao para PAID — ate la o nome precisa morar em
 * algum lugar. No pagamento, cada OrderHolder vira exatamente um Ticket.
 */
@Entity
@Table(name = "order_holder")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    @Builder.Default
    private UUID publicId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder order;

    @Column(name = "holder_index", nullable = false)
    private int holderIndex;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "document", length = 20)
    private String document;
}
