package br.com.portaria.identity;

import br.com.portaria.shared.exception.TooManyLoginAttemptsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste do freio isolado, sem Spring e sem banco.
 *
 * O relogio e controlado: a expiracao do bloqueio e verificada avancando o
 * tempo, e nao dormindo. Teste que espera o relogio real e lento e, pior, fica
 * intermitente na maquina carregada do CI.
 */
class LoginAttemptServiceTest {

    private static final String ADDRESS = "203.0.113.7";
    private static final String EMAIL = "alvo@exemplo.com";

    /** Relogio que so anda quando o teste manda. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-09-01T12:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final MovableClock clock = new MovableClock();

    private LoginAttemptService service(int perAccount, int perAddress) {
        return new LoginAttemptService(clock, perAccount, perAddress, Duration.ofMinutes(15));
    }

    @Test
    void bloqueiaAContaDepoisDoLimite() {
        var service = service(3, 100);

        for (int i = 0; i < 3; i++) {
            service.recordFailure(ADDRESS, EMAIL);
        }

        assertThatThrownBy(() -> service.assertNotBlocked(ADDRESS, EMAIL))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    /**
     * A contagem por endereco existe para o caso que a contagem por conta nao
     * ve: um atacante variando o e-mail a cada tentativa. Cada conta fica com
     * uma falha so, e nenhuma bloqueia.
     */
    @Test
    void bloqueiaOEnderecoQueVarreMuitosEmails() {
        var service = service(5, 10);

        for (int i = 0; i < 10; i++) {
            service.recordFailure(ADDRESS, "alvo" + i + "@exemplo.com");
        }

        assertThatThrownBy(() -> service.assertNotBlocked(ADDRESS, "outro@exemplo.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void oBloqueioExpiraSozinho() {
        var service = service(3, 100);
        for (int i = 0; i < 3; i++) {
            service.recordFailure(ADDRESS, EMAIL);
        }
        assertThatThrownBy(() -> service.assertNotBlocked(ADDRESS, EMAIL))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        assertThatCode(() -> service.assertNotBlocked(ADDRESS, EMAIL))
                .as("bloqueio permanente transformaria o freio em arma contra o dono da conta")
                .doesNotThrowAnyException();
    }

    @Test
    void oTempoRestanteEInformado() {
        var service = service(1, 100);
        service.recordFailure(ADDRESS, EMAIL);

        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.assertNotBlocked(ADDRESS, EMAIL))
                .isInstanceOfSatisfying(TooManyLoginAttemptsException.class, ex ->
                        assertThat(ex.getRetryAfterSeconds())
                                .isBetween(1L, Duration.ofMinutes(10).toSeconds()));
    }

    @Test
    void acertoZeraAsDuasContagens() {
        var service = service(3, 5);
        for (int i = 0; i < 3; i++) {
            service.recordFailure(ADDRESS, EMAIL);
        }

        service.recordSuccess(ADDRESS, EMAIL);

        assertThatCode(() -> service.assertNotBlocked(ADDRESS, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void enderecosDiferentesNaoSeAfetam() {
        var service = service(2, 100);
        service.recordFailure(ADDRESS, EMAIL);
        service.recordFailure(ADDRESS, EMAIL);

        assertThatCode(() -> service.assertNotBlocked("198.51.100.4", EMAIL))
                .doesNotThrowAnyException();
    }
}
