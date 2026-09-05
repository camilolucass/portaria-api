package br.com.portaria.ticket;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Ingressos", description = "Ingressos emitidos e seus QR Codes")
@PreAuthorize("hasRole('BUYER')")
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @Operation(summary = "Busca um ingresso",
            description = "Devolve o code assinado, que e o conteudo do QR (RN-09).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ingresso encontrado"),
            @ApiResponse(responseCode = "404", description = "Ingresso nao encontrado")
    })
    @GetMapping("/{publicId}")
    public TicketResponse findById(@PathVariable UUID publicId) {
        return service.findByPublicId(publicId);
    }

    @Operation(summary = "Renderiza o QR Code",
            description = "PNG 300x300 com o code assinado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagem do QR Code"),
            @ApiResponse(responseCode = "404", description = "Ingresso nao encontrado")
    })
    @GetMapping(value = "/{publicId}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrCode(@PathVariable UUID publicId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(service.renderQrCode(publicId));
    }
}
