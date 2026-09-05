package br.com.portaria.payment;

import br.com.portaria.shared.exception.InvalidWebhookSignatureException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@Tag(name = "Webhooks", description = "Notificacoes do gateway de pagamento")
@RestController
@RequestMapping("/api/v1/webhooks")
public class PaymentWebhookController {

    private static final int MINIMUM_SECRET_LENGTH = 32;

    private final PaymentWebhookService service;
    private final String secret;

    /**
     * O segredo e conferido aqui, e nao so no uso: com app.webhook.secret vazio,
     * um cabecalho X-Webhook-Secret vazio passaria na comparacao e qualquer
     * pessoa marcaria pedidos como pagos. Falhar na subida transforma um
     * problema de configuracao silencioso em erro imediato.
     */
    public PaymentWebhookController(PaymentWebhookService service,
                                    @Value("${app.webhook.secret}") String secret) {
        if (secret == null || secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.webhook.secret deve ter ao menos %d caracteres".formatted(MINIMUM_SECRET_LENGTH));
        }
        this.service = service;
        this.secret = secret;
    }

    /**
     * A rota e publica — o gateway nao tem como fazer login. O que a protege e
     * o segredo compartilhado no cabecalho, comparado em tempo constante.
     *
     * Sem ele, qualquer pessoa marcaria pedidos como pagos com um curl. Nao
     * basta a consulta ao gateway: um atacante que descobrisse a
     * payment_reference de um pedido conseguiria emitir os ingressos dele.
     */
    @Operation(summary = "Recebe notificacao de pagamento",
            description = "Idempotente: a mesma notificacao processada duas vezes nao paga o "
                    + "pedido duas vezes. A situacao vem de consulta ao gateway, nunca do corpo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificacao recebida"),
            @ApiResponse(responseCode = "400", description = "Corpo invalido"),
            @ApiResponse(responseCode = "401", description = "Assinatura do webhook invalida")
    })
    @PostMapping("/payments")
    public Map<String, String> receive(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String provided,
            @RequestBody @Valid PaymentNotification notification) {

        assertValidSecret(provided);
        var result = service.process(notification.externalId());
        return Map.of("result", result.name());
    }

    private void assertValidSecret(String provided) {
        if (provided == null
                || !MessageDigest.isEqual(provided.getBytes(UTF_8), secret.getBytes(UTF_8))) {
            throw new InvalidWebhookSignatureException();
        }
    }
}
