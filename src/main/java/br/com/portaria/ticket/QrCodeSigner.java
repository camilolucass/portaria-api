package br.com.portaria.ticket;

import br.com.portaria.shared.exception.InvalidTicketCodeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * SPEC 7.2 — assinatura do QR (problema P2).
 *
 * O conteudo do QR e {ticket.public_id}.{assinatura}, com HMAC-SHA256 sobre o
 * public_id em Base64 URL-safe sem padding. Sem a chave, ninguem produz um
 * codigo que passe na verificacao.
 */
@Component
public class QrCodeSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_LENGTH = 32;

    private final SecretKeySpec key;

    public QrCodeSigner(@Value("${app.qr.secret}") String secret) {
        if (secret == null || secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.qr.secret deve ter ao menos %d caracteres".formatted(MINIMUM_SECRET_LENGTH));
        }
        this.key = new SecretKeySpec(secret.getBytes(UTF_8), ALGORITHM);
    }

    public String sign(UUID ticketPublicId) {
        return ticketPublicId + "." + mac(ticketPublicId.toString());
    }

    /**
     * As tres saidas de erro sao a mesma excecao de proposito (RN-10): o
     * atacante nao deve descobrir se errou o formato, a assinatura ou o UUID.
     */
    public UUID verifyAndExtract(String code) {
        if (code == null) {
            throw new InvalidTicketCodeException();
        }
        String[] parts = code.split("\\.", 2);
        if (parts.length != 2) {
            throw new InvalidTicketCodeException();
        }

        // comparacao em tempo constante: evita timing attack
        if (!MessageDigest.isEqual(mac(parts[0]).getBytes(UTF_8), parts[1].getBytes(UTF_8))) {
            throw new InvalidTicketCodeException();
        }

        try {
            return UUID.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new InvalidTicketCodeException();
        }
    }

    private String mac(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(data.getBytes(UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar o codigo", e);
        }
    }
}
