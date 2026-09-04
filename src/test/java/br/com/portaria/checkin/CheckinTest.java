package br.com.portaria.checkin;

import br.com.portaria.AbstractDatabaseTest;
import br.com.portaria.batch.TicketBatchRepository;
import br.com.portaria.event.EventRepository;
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

    private Ticket openGateTicket() {
        return CheckinTestFixtures.issuedTicketWithOpenGate(
                eventRepository, batchRepository, buyerRepository, orderRepository,
                ticketRepository, "12345678900");
    }

    private String checkinBody(String code, String operator) throws Exception {
        return json(new CheckinRequest(code, operator));
    }

    // caminho feliz -----------------------------------------------------------

    @Test
    void deveLiberarEntradaDeIngressoValido() throws Exception {
        Ticket ticket = openGateTicket();
        String code = signer.sign(ticket.getPublicId());

        mockMvc.perform(post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(code, "portaria-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("GRANTED"))
                .andExpect(jsonPath("$.holderName").value("Ana Souza"))
                .andExpect(jsonPath("$.batchName").value("1o lote"))
                .andExpect(jsonPath("$.eventName").value("Festa Universitaria 2026"))
                .andExpect(jsonPath("$.checkedInAt").isNotEmpty());

        Ticket used = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(used.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(used.getCheckedInAt()).isNotNull();
        assertThat(used.getCheckedInBy()).isEqualTo("portaria-1");
    }

    // RN-11 -------------------------------------------------------------------

    @Test
    void deveRecusarSegundaEntradaDoMesmoIngressoCom409() throws Exception {
        Ticket ticket = openGateTicket();
        String code = signer.sign(ticket.getPublicId());

        mockMvc.perform(post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(code, "portaria-2")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(code, "portaria-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Ingresso ja utilizado"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("portaria-2")));
    }

    // TC-03 -------------------------------------------------------------------

    @Test
    void deveRecusarCodigoComAssinaturaAlteradaCom422() throws Exception {
        Ticket ticket = openGateTicket();
        String code = signer.sign(ticket.getPublicId());
        String tampered = flipLastCharacter(code);

        mockMvc.perform(post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(tampered, "portaria-1")))
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

        mockMvc.perform(post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(code, "portaria-1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Codigo invalido"))
                .andExpect(jsonPath("$.detail").value("O codigo apresentado nao e valido."));
    }

    @Test
    void deveRecusarCodigoSemAssinaturaComAMesmaMensagem() throws Exception {
        mockMvc.perform(post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(UUID.randomUUID().toString(), "portaria-1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Codigo invalido"))
                .andExpect(jsonPath("$.detail").value("O codigo apresentado nao e valido."));
    }

    // TC-05 -------------------------------------------------------------------

    @Test
    void deveRecusarEntradaAntesDaAberturaDosPortoesCom422() throws Exception {
        Ticket ticket = CheckinTestFixtures.issuedTicketBeforeGateOpens(
                eventRepository, batchRepository, buyerRepository, orderRepository,
                ticketRepository, "12345678900");
        String code = signer.sign(ticket.getPublicId());

        mockMvc.perform(post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(code, "portaria-1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Fora do horario de entrada"));

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.ISSUED);
    }

    // TC-06 -------------------------------------------------------------------

    @Test
    void deveRecusarEntradaDeIngressoCanceladoCom409() throws Exception {
        Ticket ticket = CheckinTestFixtures.cancelledTicket(
                eventRepository, batchRepository, buyerRepository, orderRepository,
                ticketRepository, "12345678900");
        String code = signer.sign(ticket.getPublicId());

        mockMvc.perform(post("/api/v1/checkins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkinBody(code, "portaria-1")))
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
