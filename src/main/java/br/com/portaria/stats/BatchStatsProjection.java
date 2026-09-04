package br.com.portaria.stats;

/**
 * Linha crua da consulta agregada, uma por lote. Nao sai para a API: o contrato
 * da secao 6 nao expoe issued por lote, so sold e checkedIn. O issued vem junto
 * porque o total do evento e a soma dele, e assim uma consulta resolve as duas
 * coisas.
 */
public record BatchStatsProjection(String name, int sold, long issued, long checkedIn) {
}
