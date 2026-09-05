package br.com.portaria.ticket;

import br.com.portaria.identity.CurrentUserService;
import br.com.portaria.shared.exception.TicketNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository repository;
    private final QrCodeSigner signer;
    private final QrCodeRenderer renderer;
    private final CurrentUserService currentUser;

    public TicketService(TicketRepository repository, QrCodeSigner signer,
                         QrCodeRenderer renderer, CurrentUserService currentUser) {
        this.repository = repository;
        this.signer = signer;
        this.renderer = renderer;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public TicketResponse findByPublicId(UUID publicId) {
        Ticket ticket = findEntity(publicId);
        return TicketResponse.of(ticket, signer.sign(ticket.getPublicId()));
    }

    @Transactional(readOnly = true)
    public byte[] renderQrCode(UUID publicId) {
        Ticket ticket = findEntity(publicId);
        return renderer.toPng(signer.sign(ticket.getPublicId()));
    }

    /**
     * Ingresso de um pedido da conta autenticada. O QR e um segredo: quem o tem
     * entra na festa, entao a rota nao pode servir o codigo de outra pessoa.
     */
    private Ticket findEntity(UUID publicId) {
        return repository.findByPublicIdAndOrderUserId(publicId, currentUser.require().getId())
                .orElseThrow(() -> new TicketNotFoundException(publicId));
    }
}
