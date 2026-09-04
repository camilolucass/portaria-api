package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * RN-10 — assinatura invalida devolve 422 sem revelar se o ingresso existe.
 *
 * Formato errado, assinatura errada, UUID malformado e ingresso inexistente
 * saem todos com a mesma mensagem, de proposito: o atacante nao deve conseguir
 * distinguir os casos.
 */
public class InvalidTicketCodeException extends BusinessException {

    public InvalidTicketCodeException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Codigo invalido",
                "O codigo apresentado nao e valido.");
    }
}
