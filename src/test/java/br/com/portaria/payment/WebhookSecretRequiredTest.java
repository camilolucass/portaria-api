package br.com.portaria.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * O segredo do webhook precisa ser recusado na subida quando esta ausente ou
 * curto demais.
 *
 * Sem esta checagem, app.webhook.secret vazio — que e o que acontece quando
 * WEBHOOK_SECRET nao esta no ambiente, porque o application.yml usa
 * ${WEBHOOK_SECRET:} — faz a comparacao do controller aceitar um cabecalho
 * X-Webhook-Secret vazio. Qualquer pessoa marcaria pedidos como pagos.
 *
 * O docker-compose ja recusa subir sem a variavel, mas quem roda pela IDE nao
 * passa por ele. A defesa tem que estar na aplicacao.
 */
class WebhookSecretRequiredTest {

    private static final String VALIDO = "segredo-de-webhook-com-mais-de-32-caracteres";

    @DisplayName("segredo ausente ou curto impede a aplicacao de subir")
    @ParameterizedTest(name = "secret = \"{0}\"")
    @NullSource
    @ValueSource(strings = {
            "",                                   // o caso do ${WEBHOOK_SECRET:} sem ambiente
            "curto",
            "trinta-e-um-caracteres-exatosss",    // 31: um a menos que o minimo
    })
    void recusaSegredoInvalido(String secret) {
        assertThatThrownBy(() -> new PaymentWebhookController(null, secret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.webhook.secret");
    }

    @DisplayName("segredo com 32 caracteres ou mais e aceito")
    @ParameterizedTest(name = "tamanho = {0}")
    @ValueSource(ints = {32, 33, 64})
    void aceitaSegredoValido(int tamanho) {
        String secret = "x".repeat(tamanho);
        assertThat(secret).hasSize(tamanho);

        assertThatCode(() -> new PaymentWebhookController(null, secret))
                .doesNotThrowAnyException();
    }

    @DisplayName("o segredo usado nos demais testes atende ao minimo")
    @org.junit.jupiter.api.Test
    void segredoDeTesteEhValido() {
        assertThatCode(() -> new PaymentWebhookController(null, VALIDO))
                .doesNotThrowAnyException();
    }
}
