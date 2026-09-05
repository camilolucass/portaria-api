package br.com.portaria.checkin;

import br.com.portaria.AbstractDatabaseTest;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.event.EventRepository;
import br.com.portaria.event.EventStaffRepository;
import br.com.portaria.identity.AppUser;
import br.com.portaria.order.BuyerRepository;
import br.com.portaria.order.OrderRepository;
import br.com.portaria.ticket.QrCodeSigner;
import br.com.portaria.ticket.Ticket;
import br.com.portaria.ticket.TicketRepository;
import br.com.portaria.ticket.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TC-03, TC-04, TC-05, TC-06 e o caminho feliz do check-in. */
class CheckinTest extends AbstractDatabaseTest {

    @Autowired
    private QrCodeSigner signer;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketBatchRepository batchRepository;

    @Autowired
    private BuyerRepository buyerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EventStaffRepository eventStaffRepository;

    private AppUser gate;

    private Ticket openGateTicket() {
        var scenario = CheckinTestFixtures.issuedTicketWithOpenGate(
                eventRepository, batchRepository, buyerRepository, orderRepository,
                ticketRepository, eventStaffRepository, userRepository, "12345678900");
        gate = scenario.gate();
        return scenario.ticket();
    }

    private String checkinBody(String code) throws Exception {
        return json(new CheckinRequest(code));
    }

    // caminho feliz -----------------------------------------------------------

    @Test
    void deveLiberarEntradaDeIngressoValido() throws Exception {
        Ticket ticket = openGateTicket();
        String code = signer.sign(ticket.getPublicId());

        performAs(gate, post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("GRANTED"))
                .andExpect(jsonPath("$.holderName").value("Ana Souza"))
                .andExpect(jsonPath("$.batchName").value("1o lote"))
                .andExpect(jsonPath("$.eventName").value("Festa Universitaria 2026"))
                .andExpect(jsonPath("$.checkedInAt").isNotEmpty());

        Ticket used = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(used.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(used.getCheckedInAt()).isNotNull();
        assertThat(used.getCheckedInBy().getId()).isEqualTo(gate.getId());
    }

    // RN-11 -------------------------------------------------------------------

    @Test
    void deveRecusarSegundaEntradaDoMesmoIngressoCom409() throws Exception {
        Ticket ticket = openGateTicket();
        String code = signer.sign(ticket.getPublicId());

        performAs(gate, post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(code)))
                .andExpect(status().isOk());

        performAs(gate, post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(code)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Ingresso ja utilizado"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString(gate.getName())));
    }

    // TC-03 -------------------------------------------------------------------

    @Test
    void deveRecusarCodigoComAssinaturaAlteradaCom422() throws Exception {
        Ticket ticket = openGateTicket();
        String code = signer.sign(ticket.getPublicId());
        String tampered = flipLastCharacter(code);

        performAs(gate, post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(tampered)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Codigo invalido"))
                .andExpect(jsonPath("$.detail").value("O codigo apresentado nao e valido."));

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.ISSUED);
    }

    // TC-04 -------------------------------------------------------------------

    @Test
    void deveRecusarCodigoAssinadoDeIngressoInexistenteComAMesmaMensagem() throws Exception {
        openGateTicket();
        // codigo perfeitamente assinado, so que para um UUID que nao existe
        String code = signer.sign(UUID.randomUUID());

        performAs(gate, post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(code)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Codigo invalido"))
                .andExpect(jsonPath("$.detail").value("O codigo apresentado nao e valido."));
    }

    @Test
    void deveRecusarCodigoSemAssinaturaComAMesmaMensagem() throws Exception {
        openGateTicket();
        performAs(gate, post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(UUID.randomUUID().toString())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Codigo invalido"))
                .andExpect(jsonPath("$.detail").value("O codigo apresentado nao e valido."));
    }

    // TC-05 -------------------------------------------------------------------

    @Test
    void deveRecusarEntradaAntesDaAberturaDosPortoesCom422() throws Exception {
        var scenario = CheckinTestFixtures.issuedTicketBeforeGateOpens(
                eventRepository, batchRepository, buyerRepository, orderRepository,
                ticketRepository, eventStaffRepository, userRepository, "12345678900");
        gate = scenario.gate();
        Ticket ticket = scenario.ticket();
        String code = signer.sign(ticket.getPublicId());

        performAs(gate, post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(code)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Fora do horario de entrada"));

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.ISSUED);
    }

    // TC-06 -------------------------------------------------------------------

    @Test
    void deveRecusarEntradaDeIngressoCanceladoCom409() throws Exception {
        var scenario = CheckinTestFixtures.cancelledTicket(
                eventRepository, batchRepository, buyerRepository, orderRepository,
                ticketRepository, eventStaffRepository, userRepository, "12345678900");
        gate = scenario.gate();
        Ticket ticket = scenario.ticket();
        String code = signer.sign(ticket.getPublicId());

        performAs(gate, post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(code)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Ingresso cancelado"));

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.CANCELLED);
    }

    private static String flipLastCharacter(String code) {
        char last = code.charAt(code.length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        return code.substring(0, code.length() - 1) + replacement;
    }
}
