package br.com.portaria.batch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateBatchRequest(

        @NotBlank(message = "O nome do lote e obrigatorio")
        @Size(max = 60, message = "O nome do lote deve ter no maximo 60 caracteres")
        String name,

        @Positive(message = "O preco deve ser maior que zero")
        int priceCents,

        @Positive(message = "A quantidade total deve ser maior que zero")
        int totalQuantity,

        @NotNull(message = "O inicio das vendas e obrigatorio")
        LocalDateTime salesStart,

        @NotNull(message = "O fim das vendas e obrigatorio")
        LocalDateTime salesEnd
) {
}
