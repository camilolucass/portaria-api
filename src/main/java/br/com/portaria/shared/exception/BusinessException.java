package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Raiz de toda falha de regra de negocio. O status e o titulo viajam junto da
 * excecao para que o GlobalExceptionHandler nao precise conhecer cada subclasse.
 * Titulo e detalhe em portugues; nome da classe em ingles (SPEC secao 2).
 */
public abstract class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    protected BusinessException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
