package br.com.portaria.ticket;

import br.com.portaria.shared.exception.TicketNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository repository;
    private final QrCodeSigner signer;
    private final QrCodeRenderer renderer;

    public TicketService(TicketRepository repository, QrCodeSigner signer, QrCodeRenderer renderer) {
        this.repository = repository;
        this.signer = signer;
        this.renderer = renderer;
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

    private Ticket findEntity(UUID publicId) {
        return repository.findByPublicId(publicId)
                .orElseThrow(() -> new TicketNotFoundException(publicId));
    }
}
