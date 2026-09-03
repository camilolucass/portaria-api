package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Datas incoerentes entre si. Sao as mesmas condicoes dos CHECK do banco
 * (event_period_ck, event_gate_ck, batch_sales_ck) — validadas antes para
 * devolver 422 com mensagem util em vez de deixar o banco estourar 500.
 */
public class InvalidPeriodException extends BusinessException {

    public InvalidPeriodException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Periodo invalido", detail);
    }
}
