package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class OrderNotFoundException extends BusinessException {

    public OrderNotFoundException(UUID publicId) {
        super(HttpStatus.NOT_FOUND, "Pedido nao encontrado",
                "Nao existe pedido com o identificador %s.".formatted(publicId));
    }
}
