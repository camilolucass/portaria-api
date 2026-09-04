package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/** RN-11 — ingresso cancelado devolve 409. */
public class TicketCancelledException extends BusinessException {

    public TicketCancelledException() {
        super(HttpStatus.CONFLICT, "Ingresso cancelado",
                "Este ingresso foi cancelado e nao permite entrada.");
    }
}
