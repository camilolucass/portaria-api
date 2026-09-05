package br.com.portaria.demo;

import br.com.portaria.batch.TicketBatch;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.event.Event;
import br.com.portaria.event.EventRepository;
import br.com.portaria.event.EventStaff;
import br.com.portaria.event.EventStaffRepository;
import br.com.portaria.event.EventStatus;
import br.com.portaria.identity.AppUser;
import br.com.portaria.identity.AppUserRepository;
import br.com.portaria.identity.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Evento publicado e dois lotes, para o Swagger ter o que mostrar sem preparo
 * manual.
 *
 * NAO e uma migration. O SPEC pede o seed como V2__seed_demo.sql, mas migration
 * roda em todo banco que o Flyway alcanca, producao inclusive — e tirar uma
 * migration ja aplicada do classpath depois quebra a validacao do proprio
 * Flyway. Como bean de perfil, o dado de demonstracao existe onde deve existir
 * e some sem deixar rastro no historico.
 *
 * Ativa so com o perfil dev: SPRING_PROFILES_ACTIVE=dev
 */
@Profile("dev")
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final UUID DEMO_EVENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID FIRST_BATCH_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SECOND_BATCH_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    /**
     * Senha unica para as tres contas de demonstracao. Obviamente falsa e
     * versionada de proposito: so existe sob o perfil dev, e deixar cada
     * desenvolvedor inventar a sua daria mais chance de alguem reaproveitar uma
     * senha real aqui.
     */
    private static final String DEMO_PASSWORD = "portaria-dev-2026";

    private static final String ORGANIZER_EMAIL = "organizador@exemplo.com";
    private static final String GATE_EMAIL = "portaria@exemplo.com";
    private static final String BUYER_EMAIL = "comprador@exemplo.com";

    private final EventRepository eventRepository;
    private final TicketBatchRepository batchRepository;
    private final AppUserRepository userRepository;
    private final EventStaffRepository eventStaffRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(EventRepository eventRepository,
                          TicketBatchRepository batchRepository,
                          AppUserRepository userRepository,
                          EventStaffRepository eventStaffRepository,
                          PasswordEncoder passwordEncoder) {
        this.eventRepository = eventRepository;
        this.batchRepository = batchRepository;
        this.userRepository = userRepository;
        this.eventStaffRepository = eventStaffRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Idempotente: reiniciar a aplicacao nao duplica o evento de demonstracao. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUsers();

        if (eventRepository.findByPublicId(DEMO_EVENT_ID).isPresent()) {
            return;
        }

        AppUser organizer = userRepository.findByEmail(ORGANIZER_EMAIL).orElseThrow();

        LocalDateTime now = LocalDateTime.now();
        Event event = eventRepository.save(Event.builder()
                .publicId(DEMO_EVENT_ID)
                .organizer(organizer)
                .name("Festa Universitaria 2026")
                .description("Open bar ate as 2h. Dado de demonstracao, perfil dev.")
                .venue("Centro de Eventos, Orleans/SC")
                .gateOpensAt(now.plusDays(30))
                .startsAt(now.plusDays(30).plusHours(1))
                .endsAt(now.plusDays(30).plusHours(7))
                .status(EventStatus.PUBLISHED)
                .build());

        batchRepository.save(TicketBatch.builder()
                .publicId(FIRST_BATCH_ID)
                .event(event)
                .name("1o lote")
                .priceCents(4500)
                .totalQuantity(200)
                .soldQuantity(0)
                .salesStart(now.minusDays(1))
                .salesEnd(now.plusDays(20))
                .build());

        batchRepository.save(TicketBatch.builder()
                .publicId(SECOND_BATCH_ID)
                .event(event)
                .name("2o lote")
                .priceCents(6000)
                .totalQuantity(300)
                .soldQuantity(0)
                .salesStart(now.plusDays(20))
                .salesEnd(now.plusDays(29))
                .build());

        // sem este vinculo a portaria de demonstracao nao consegue validar nada
        AppUser gate = userRepository.findByEmail(GATE_EMAIL).orElseThrow();
        eventStaffRepository.save(new EventStaff(event, gate));

        log.info("Perfil dev: evento de demonstracao criado com 2 lotes e portaria vinculada");
    }

    /** Uma conta por papel. Nao ha auto-cadastro: e assim que usuarios nascem. */
    private void seedUsers() {
        createIfAbsent(ORGANIZER_EMAIL, "Organizadora Demo", Set.of(Role.ORGANIZER));
        createIfAbsent(GATE_EMAIL, "Portaria Demo", Set.of(Role.GATE));
        createIfAbsent(BUYER_EMAIL, "Comprador Demo", Set.of(Role.BUYER));
    }

    private void createIfAbsent(String email, String name, Set<Role> roles) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(AppUser.builder()
                .name(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .roles(java.util.EnumSet.copyOf(roles))
                .build());
        log.info("Perfil dev: usuario {} criado", email);
    }
}
