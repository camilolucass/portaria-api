package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TicketNotFoundException extends BusinessException {

    public TicketNotFoundException(UUID publicId) {
        super(HttpStatus.NOT_FOUND, "Ingresso nao encontrado",
                "Nao existe ingresso com o identificador %s.".formatted(publicId));
    }
}
