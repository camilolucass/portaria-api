package br.com.portaria.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckinRequest(

        @NotBlank(message = "O codigo e obrigatorio")
        String code,

        @NotBlank(message = "O operador e obrigatorio")
        @Size(max = 120, message = "O operador deve ter no maximo 120 caracteres")
        String operator
) {
}
