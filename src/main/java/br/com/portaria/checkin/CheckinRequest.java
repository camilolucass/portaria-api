package br.com.portaria.checkin;

import jakarta.validation.constraints.NotBlank;

/**
 * O campo operator da secao 6 do SPEC saiu na Fase 2.
 *
 * Deixar o cliente dizer quem validou a entrada e deixar o auditado escrever a
 * propria auditoria: bastaria enviar o nome de outra portaria para lavar a
 * responsabilidade de uma entrada indevida. Agora vem do token.
 */
public record CheckinRequest(

        @NotBlank(message = "O codigo e obrigatorio")
        String code
) {
}
