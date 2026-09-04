package br.com.portaria.checkin;

import br.com.portaria.ticket.Ticket;

import java.time.LocalDateTime;

public record CheckinResult(
        String result,
        String holderName,
        String batchName,
        String eventName,
        LocalDateTime checkedInAt
) {

    /**
     * O checkedInAt vem do instante enviado ao UPDATE, nao da entidade: o bulk
     * update nao atualiza o objeto em memoria.
     */
    static CheckinResult granted(Ticket ticket, LocalDateTime checkedInAt) {
        return new CheckinResult(
                "GRANTED",
                ticket.getHolderName(),
                ticket.getBatch().getName(),
                ticket.getBatch().getEvent().getName(),
                checkedInAt);
    }
}
