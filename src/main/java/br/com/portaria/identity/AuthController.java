package br.com.portaria.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticacao", description = "Emissao de token de acesso")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @Operation(summary = "Autentica e emite o token",
            description = "Devolve um JWT para o cabecalho Authorization: Bearer. "
                    + "Nao ha auto-cadastro: as contas nascem por seed ou provisionamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token emitido"),
            @ApiResponse(responseCode = "400", description = "Campos invalidos"),
            @ApiResponse(responseCode = "401", description = "Credenciais invalidas"),
            @ApiResponse(responseCode = "429", description = "Tentativas demais; veja Retry-After")
    })
    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request,
                               HttpServletRequest httpRequest) {
        // getRemoteAddr, e nao X-Forwarded-For: confiar no cabecalho deixaria
        // qualquer um driblar o freio inventando um IP. Atras de proxy, use
        // server.forward-headers-strategy para o Spring resolver a partir de
        // um proxy confiavel
        return service.login(request, httpRequest.getRemoteAddr());
    }
}
