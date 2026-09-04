package br.com.portaria.shared.exception;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** RN-11 — ingresso ja utilizado devolve 409 informando quando e por quem. */
public class TicketAlreadyUsedException extends BusinessException {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm");

    private final LocalDateTime checkedInAt;

    public TicketAlreadyUsedException(LocalDateTime checkedInAt, String checkedInBy) {
        super(HttpStatus.CONFLICT, "Ingresso ja utilizado", detail(checkedInAt, checkedInBy));
        this.checkedInAt = checkedInAt;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    private static String detail(LocalDateTime checkedInAt, String checkedInBy) {
        if (checkedInAt == null) {
            return "Este ingresso ja foi validado.";
        }
        return "Este ingresso foi validado em %s por %s."
                .formatted(FORMAT.format(checkedInAt),
                        checkedInBy == null ? "outra portaria" : checkedInBy);
    }
}
