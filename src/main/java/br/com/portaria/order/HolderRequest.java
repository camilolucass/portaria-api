package br.com.portaria.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HolderRequest(

        @NotBlank(message = "O nome do titular e obrigatorio")
        @Size(max = 120, message = "O nome do titular deve ter no maximo 120 caracteres")
        String name,

        @Size(max = 20, message = "O documento do titular deve ter no maximo 20 caracteres")
        String document
) {
}
