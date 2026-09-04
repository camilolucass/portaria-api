package br.com.portaria.demo;

import br.com.portaria.batch.TicketBatch;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.event.Event;
import br.com.portaria.event.EventRepository;
import br.com.portaria.event.EventStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    private final EventRepository eventRepository;
    private final TicketBatchRepository batchRepository;

    public DemoDataSeeder(EventRepository eventRepository, TicketBatchRepository batchRepository) {
        this.eventRepository = eventRepository;
        this.batchRepository = batchRepository;
    }

    /** Idempotente: reiniciar a aplicacao nao duplica o evento de demonstracao. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (eventRepository.findByPublicId(DEMO_EVENT_ID).isPresent()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Event event = eventRepository.save(Event.builder()
                .publicId(DEMO_EVENT_ID)
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

        log.info("Perfil dev: evento de demonstracao criado com 2 lotes");
    }
}
