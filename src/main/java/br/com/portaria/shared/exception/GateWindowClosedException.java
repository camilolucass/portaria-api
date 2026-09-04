package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/** RN-12 — check-in so dentro de [gate_opens_at, ends_at]. */
public class GateWindowClosedException extends BusinessException {

    public GateWindowClosedException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Fora do horario de entrada", detail);
    }
}
