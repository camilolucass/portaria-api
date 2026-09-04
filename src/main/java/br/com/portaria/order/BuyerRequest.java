package br.com.portaria.order;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BuyerRequest(

        @NotBlank(message = "O nome do comprador e obrigatorio")
        @Size(max = 120, message = "O nome do comprador deve ter no maximo 120 caracteres")
        String name,

        @NotBlank(message = "O e-mail do comprador e obrigatorio")
        @Email(message = "O e-mail do comprador e invalido")
        @Size(max = 160, message = "O e-mail deve ter no maximo 160 caracteres")
        String email,

        @NotBlank(message = "O documento do comprador e obrigatorio")
        @Size(max = 20, message = "O documento deve ter no maximo 20 caracteres")
        String document
) {
}
