package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/** Notificacao sem o segredo compartilhado, ou com segredo errado. */
public class InvalidWebhookSignatureException extends BusinessException {

    public InvalidWebhookSignatureException() {
        super(HttpStatus.UNAUTHORIZED, "Webhook nao autenticado",
                "Assinatura do webhook ausente ou invalida.");
    }
}
