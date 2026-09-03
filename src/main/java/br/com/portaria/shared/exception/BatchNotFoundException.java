package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class BatchNotFoundException extends BusinessException {

    public BatchNotFoundException(UUID publicId) {
        super(HttpStatus.NOT_FOUND, "Lote nao encontrado",
                "Nao existe lote com o identificador %s.".formatted(publicId));
    }
}
