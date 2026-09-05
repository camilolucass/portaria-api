package br.com.portaria.payment;

/** Situacao de um pagamento no gateway, normalizada para o vocabulario daqui. */
public enum PaymentStatus {
    APPROVED, PENDING, REJECTED
}
