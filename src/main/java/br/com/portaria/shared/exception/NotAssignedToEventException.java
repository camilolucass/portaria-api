package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/** Fase 2 — operador de portaria tentando validar entrada de evento ao qual nao esta vinculado. */
public class NotAssignedToEventException extends BusinessException {

    public NotAssignedToEventException(String eventName) {
        super(HttpStatus.FORBIDDEN, "Portaria nao vinculada ao evento",
                "Sua conta nao opera a portaria do evento \"%s\".".formatted(eventName));
    }
}
