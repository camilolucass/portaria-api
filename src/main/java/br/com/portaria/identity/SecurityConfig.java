package br.com.portaria.identity;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

import static java.nio.charset.StandardCharsets.UTF_8;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final int MINIMUM_SECRET_LENGTH = 32;

    private final SecretKeySpec key;

    public SecurityConfig(@Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.jwt.secret deve ter ao menos %d caracteres".formatted(MINIMUM_SECRET_LENGTH));
        }
        this.key = new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ProblemDetailSecurityHandlers problemHandlers)
            throws Exception {
        return http
                // sem cookie e sem sessao: o token vai no cabecalho Authorization,
                // entao nao ha o que um site de terceiros possa disparar por CSRF
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // a documentacao descreve a API, nao expoe dado: fica aberta
                        .requestMatchers("/docs", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // as paginas sao HTML e JS estaticos, sem nenhum dado dentro:
                        // tudo que elas mostram vem da API, que continua exigindo token
                        .requestMatchers("/", "/index.html", "/*.css", "/*.js",
                                "/organizador.html", "/comprador.html", "/portaria.html",
                                "/favicon.ico").permitAll()
                        // qualquer rota nova nasce fechada, e nao aberta por esquecimento
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problemHandlers))
                // 401 e 403 tambem em RFC 7807, como todo o resto da API
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .build();
    }

    /**
     * O prefixo ROLE_ e detalhe do Spring Security: o token carrega a claim
     * "roles" com os nomes limpos (ORGANIZER, GATE, BUYER) e a conversao para
     * ROLE_X acontece so aqui dentro.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Delegating encoder: o hash nasce com o prefixo {bcrypt}, entao trocar de
     * algoritmo depois nao invalida as senhas ja gravadas.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
