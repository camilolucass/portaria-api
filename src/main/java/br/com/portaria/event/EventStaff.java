package br.com.portaria.event;

import br.com.portaria.identity.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/** Vinculo entre um operador de portaria e um evento. */
@Entity
@Table(name = "event_staff")
@IdClass(EventStaff.EventStaffId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventStaff {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /**
     * Classe comum, e nao record: o @IdClass do JPA e lido no padrao JavaBean,
     * e os acessores de um record (event(), user()) nao atendem esse contrato.
     * Os campos precisam se chamar como os @Id da entidade e ter o tipo da
     * chave da entidade associada.
     */
    public static class EventStaffId implements Serializable {

        private Long event;
        private Long user;

        public EventStaffId() {
        }

        public EventStaffId(Long event, Long user) {
            this.event = event;
            this.user = user;
        }

        public Long getEvent() {
            return event;
        }

        public void setEvent(Long event) {
            this.event = event;
        }

        public Long getUser() {
            return user;
        }

        public void setUser(Long user) {
            this.user = user;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof EventStaffId id
                    && Objects.equals(event, id.event)
                    && Objects.equals(user, id.user);
        }

        @Override
        public int hashCode() {
            return Objects.hash(event, user);
        }
    }
}
