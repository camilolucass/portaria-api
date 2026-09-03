package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class EventNotFoundException extends BusinessException {

    public EventNotFoundException(UUID publicId) {
        super(HttpStatus.NOT_FOUND, "Evento nao encontrado",
                "Nao existe evento com o identificador %s.".formatted(publicId));
    }
}
