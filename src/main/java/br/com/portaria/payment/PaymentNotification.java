package br.com.portaria.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo da notificacao. So o identificador — de proposito.
 *
 * Aceitar a situacao do pagamento aqui seria deixar qualquer um marcar pedidos
 * como pagos com um curl. Tudo que decide vem da consulta ao gateway.
 */
public record PaymentNotification(

        @NotBlank(message = "O identificador do pagamento e obrigatorio")
        @Size(max = 120, message = "O identificador deve ter no maximo 120 caracteres")
        String externalId
) {
}
