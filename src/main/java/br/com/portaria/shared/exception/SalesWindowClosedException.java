package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/** RN-02: lote so vende dentro de [sales_start, sales_end]. TC-10. */
public class SalesWindowClosedException extends BusinessException {

    public SalesWindowClosedException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Fora da janela de vendas", detail);
    }
}
