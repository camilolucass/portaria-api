package br.com.portaria.payment;

import java.util.UUID;

/**
 * O que o gateway responde quando perguntamos sobre um pagamento.
 *
 * A situacao vem daqui, nunca do corpo da notificacao: quem posta no webhook e
 * a internet inteira, e um corpo dizendo "aprovado" nao prova pagamento nenhum.
 */
public record GatewayPayment(String externalId, UUID paymentReference, PaymentStatus status) {
}
