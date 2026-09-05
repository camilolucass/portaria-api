package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Freio de forca bruta acionado.
 *
 * O 429 vale igual para conta existente e inexistente: se so as existentes
 * fossem bloqueadas, o proprio bloqueio viraria o oraculo que a mensagem
 * generica de credenciais tenta evitar.
 */
public class TooManyLoginAttemptsException extends BusinessException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas",
                "Tentativas de login demais. Tente novamente em %d segundo(s)."
                        .formatted(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
