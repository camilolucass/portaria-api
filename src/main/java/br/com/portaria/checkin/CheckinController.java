package br.com.portaria.checkin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Check-in", description = "Validacao de entrada na portaria")
@RestController
@RequestMapping("/api/v1/checkins")
public class CheckinController {

    private final CheckinService service;

    public CheckinController(CheckinService service) {
        this.service = service;
    }

    @Operation(summary = "Valida a entrada",
            description = "Exige o papel GATE e vinculo com o evento. Atomico: entre varias portarias lendo o mesmo codigo ao mesmo tempo, exatamente uma entrada e liberada (RN-13). Assinatura invalida e ingresso inexistente devolvem a mesma mensagem, de proposito (RN-10).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrada liberada"),
            @ApiResponse(responseCode = "400", description = "Campos invalidos"),
            @ApiResponse(responseCode = "403", description = "Portaria nao vinculada ao evento"),
            @ApiResponse(responseCode = "409", description = "Ingresso ja utilizado ou cancelado"),
            @ApiResponse(responseCode = "422", description = "Codigo invalido ou fora do horario de entrada")
    })
    @PreAuthorize("hasRole('GATE')")
    @PostMapping
    public CheckinResult checkIn(@RequestBody @Valid CheckinRequest request) {
        return service.checkIn(request.code());
    }
}
