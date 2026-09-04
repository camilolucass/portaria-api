package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/** Transicao de pedido recusada: pagar algo que nao esta PENDING, por exemplo. */
public class InvalidOrderStatusException extends BusinessException {

    public InvalidOrderStatusException(String detail) {
        super(HttpStatus.CONFLICT, "Situacao do pedido nao permite a operacao", detail);
    }
}
