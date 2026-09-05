package br.com.portaria.identity;

import br.com.portaria.shared.exception.TooManyLoginAttemptsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Freio de forca bruta no login.
 *
 * Sem isto, da para varrer senhas indefinidamente. E o BCrypt, que e lento de
 * proposito, deixa de ser so uma defesa: vira um jeito barato de consumir CPU
 * do servidor, porque cada tentativa custa muito mais para nos do que para
 * quem tenta.
 *
 * Duas contagens, com propositos diferentes:
 *
 * - por (IP, e-mail): protege uma conta especifica de ter a senha adivinhada
 * - por IP: impede que o mesmo atacante varra centenas de e-mails diferentes,
 *   o que passaria despercebido pela primeira contagem
 *
 * O bloqueio expira sozinho. Bloqueio permanente transformaria o freio em uma
 * arma: bastaria errar a senha de alguem cinco vezes para deixar a pessoa de
 * fora ate um humano intervir.
 */
@Service
public class LoginAttemptService {

    private final Clock clock;
    private final int maxPerAccount;
    private final int maxPerAddress;
    private final Duration block;

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(Clock clock,
                               @Value("${app.login.max-attempts-per-account}") int maxPerAccount,
                               @Value("${app.login.max-attempts-per-address}") int maxPerAddress,
                               @Value("${app.login.block-duration}") Duration block) {
        this.clock = clock;
        this.maxPerAccount = maxPerAccount;
        this.maxPerAddress = maxPerAddress;
        this.block = block;
    }

    /** Chamado antes de gastar um hash de senha. Bloqueado nao chega no BCrypt. */
    public void assertNotBlocked(String address, String email) {
        long accountRetry = retryAfterFor(accountKey(address, email), maxPerAccount);
        long addressRetry = retryAfterFor(address, maxPerAddress);
        long retryAfter = Math.max(accountRetry, addressRetry);

        if (retryAfter > 0) {
            throw new TooManyLoginAttemptsException(retryAfter);
        }
    }

    public void recordFailure(String address, String email) {
        register(accountKey(address, email));
        register(address);
    }

    /** Acerto zera as contagens: quem sabe a senha nao e quem esta varrendo. */
    public void recordSuccess(String address, String email) {
        attempts.remove(accountKey(address, email));
        attempts.remove(address);
    }

    private long retryAfterFor(String key, int limit) {
        Attempts current = attempts.get(key);
        if (current == null) {
            return 0;
        }
        Instant now = Instant.now(clock);
        if (now.isAfter(current.until)) {
            attempts.remove(key);
            return 0;
        }
        if (current.count.get() < limit) {
            return 0;
        }
        return Math.max(1, Duration.between(now, current.until).toSeconds());
    }

    private void register(String key) {
        Instant now = Instant.now(clock);
        attempts.compute(key, (ignored, current) -> {
            if (current == null || now.isAfter(current.until)) {
                return new Attempts(new AtomicInteger(1), now.plus(block));
            }
            current.count.incrementAndGet();
            return current;
        });
        evictExpiredIfCrowded(now);
    }

    /**
     * O mapa cresce com chaves que ninguem mais consulta — um atacante variando
     * e-mail cria uma entrada por tentativa. A limpeza roda so quando o mapa
     * passa do limite, para nao pagar varredura em toda requisicao.
     */
    private void evictExpiredIfCrowded(Instant now) {
        if (attempts.size() <= 10_000) {
            return;
        }
        attempts.entrySet().removeIf(entry -> now.isAfter(entry.getValue().until));
    }

    private String accountKey(String address, String email) {
        return address + "|" + email;
    }

    private record Attempts(AtomicInteger count, Instant until) {
    }
}
