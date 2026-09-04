package br.com.portaria.stats;

import java.util.List;

/**
 * Contrato da secao 6 do SPEC.
 *
 * totalRevenueCents e long, e nao int como os valores monetarios individuais:
 * um agregado em int estoura em R$ 21,4 milhoes, e overflow de inteiro nao
 * levanta erro em Java — o total viraria negativo em silencio.
 */
public record EventStatsResponse(
        long totalIssued,
        long totalCheckedIn,
        long totalRevenueCents,
        List<BatchStatsResponse> byBatch
) {
}
