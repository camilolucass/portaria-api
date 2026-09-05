package br.com.portaria.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

/**
 * Cria o primeiro organizador a partir do ambiente.
 *
 * Nao ha auto-cadastro, e o seeder de demonstracao so roda no perfil dev — sem
 * isto, um deploy real subiria sem nenhuma conta e ninguem conseguiria sequer
 * logar. A alternativa seria uma rota publica de cadastro aceitando o papel,
 * que e escalonamento de privilegio por construcao.
 *
 * So age quando as duas variaveis estao definidas, e nunca sobrescreve uma
 * conta existente: reiniciar a aplicacao nao redefine a senha de ninguem.
 */
@Component
public class OrganizerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrganizerBootstrap.class);
    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public OrganizerBootstrap(AppUserRepository repository,
                              PasswordEncoder passwordEncoder,
                              @Value("${app.bootstrap.organizer.email:}") String email,
                              @Value("${app.bootstrap.organizer.password:}") String password) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (password.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "app.bootstrap.organizer.password deve ter ao menos %d caracteres"
                            .formatted(MINIMUM_PASSWORD_LENGTH));
        }

        String normalized = AppUserDetailsService.normalize(email);
        if (repository.existsByEmail(normalized)) {
            return;
        }

        repository.save(AppUser.builder()
                .name("Organizador")
                .email(normalized)
                .passwordHash(passwordEncoder.encode(password))
                .roles(EnumSet.of(Role.ORGANIZER))
                .build());

        log.info("Organizador inicial criado a partir do ambiente: {}", normalized);
    }
}
