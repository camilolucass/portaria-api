package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/** RN-04: reserva recusada por falta de estoque. */
public class SoldOutException extends BusinessException {

    public SoldOutException(String detail) {
        super(HttpStatus.CONFLICT, "Lote esgotado", detail);
    }
}
