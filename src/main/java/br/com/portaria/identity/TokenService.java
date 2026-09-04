package br.com.portaria.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class TokenService {

    private static final String ISSUER = "portaria-api";

    private final JwtEncoder encoder;
    private final Duration expiration;

    public TokenService(JwtEncoder encoder,
                        @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.encoder = encoder;
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    /**
     * O subject e o public_id, nunca o id interno nem o e-mail: o token circula
     * fora do servidor e a convencao do SPEC e que id interno nao aparece.
     */
    public IssuedToken issueFor(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiration);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getPublicId().toString())
                .claim("roles", user.getRoles().stream().map(Enum::name).toList())
                .build();

        // o header precisa dizer HS256 explicitamente: sem isso o Nimbus procura
        // uma chave RS256, nao acha, e falha com "Failed to select a JWK signing key"
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt,
                user.getRoles().stream().map(Enum::name).sorted().toList());
    }

    public record IssuedToken(String token, Instant expiresAt, List<String> roles) {
    }
}
