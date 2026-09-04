package br.com.portaria.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(

        @NotNull(message = "O lote e obrigatorio")
        UUID batchId,

        @Min(value = 1, message = "A quantidade deve ser de no minimo 1 ingresso")
        @Max(value = 6, message = "A quantidade deve ser de no maximo 6 ingressos")
        int quantity,

        @NotNull(message = "Os dados do comprador sao obrigatorios")
        @Valid
        BuyerRequest buyer,

        @NotEmpty(message = "Informe ao menos um titular")
        @Valid
        List<HolderRequest> holders
) {

    /**
     * TC-09 — quantity = 2 com 3 holders devolve 400.
     *
     * E uma regra entre campos, entao vive no proprio DTO como Bean Validation:
     * assim ela sai como 400 junto das outras violacoes, e nao como 422 de
     * regra de negocio.
     */
    @AssertTrue(message = "A quantidade de titulares deve ser igual a quantidade de ingressos")
    public boolean isHoldersMatchingQuantity() {
        return holders != null && holders.size() == quantity;
    }
}
