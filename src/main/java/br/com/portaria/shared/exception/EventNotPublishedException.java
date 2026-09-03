package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/** RN-01: so um evento PUBLISHED aceita venda. */
public class EventNotPublishedException extends BusinessException {

    public EventNotPublishedException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Evento nao esta a venda", detail);
    }
}
