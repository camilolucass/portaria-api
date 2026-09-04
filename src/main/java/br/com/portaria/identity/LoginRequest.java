package br.com.portaria.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "O e-mail e obrigatorio")
        @Size(max = 160, message = "O e-mail deve ter no maximo 160 caracteres")
        String email,

        @NotBlank(message = "A senha e obrigatoria")
        @Size(max = 200, message = "A senha deve ter no maximo 200 caracteres")
        String password
) {
}
