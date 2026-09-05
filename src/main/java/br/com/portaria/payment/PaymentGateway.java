package br.com.portaria.payment;

import java.util.Optional;

/**
 * Porta para o gateway de pagamento.
 *
 * A notificacao traz apenas um identificador; tudo que decide alguma coisa vem
 * desta consulta. E aqui que a implementacao do Mercado Pago entra na Fase 3,
 * sem que nada do fluxo de webhook precise mudar.
 */
public interface PaymentGateway {

    Optional<GatewayPayment> find(String externalId);
}
