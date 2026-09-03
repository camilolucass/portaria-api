package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/** Transicao de status recusada: publicar um evento que nao esta em DRAFT. */
public class InvalidEventStatusException extends BusinessException {

    public InvalidEventStatusException(String detail) {
        super(HttpStatus.CONFLICT, "Situacao do evento nao permite a operacao", detail);
    }
}
