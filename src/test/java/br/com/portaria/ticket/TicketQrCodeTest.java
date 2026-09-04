package br.com.portaria.ticket;

import br.com.portaria.AbstractIntegrationTest;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RN-09 e a renderizacao do PNG. */
@Transactional
class TicketQrCodeTest extends AbstractIntegrationTest {

    @Autowired
    private QrCodeSigner signer;

    @Autowired
    private QrCodeRenderer renderer;

    @Test
    void codigoDeveSerPublicIdMaisAssinaturaBase64UrlSafeSemPadding() {
        UUID publicId = UUID.randomUUID();
        String code = signer.sign(publicId);

        String[] parts = code.split("\\.", 2);
        assertThat(parts[0]).isEqualTo(publicId.toString());
        assertThat(parts[1])
                .doesNotContain("=")
                .doesNotContain("+")
                .doesNotContain("/")
                .matches("[A-Za-z0-9_-]+");
    }

    @Test
    void deveExtrairOMesmoPublicIdQueAssinou() {
        UUID publicId = UUID.randomUUID();

        assertThat(signer.verifyAndExtract(signer.sign(publicId))).isEqualTo(publicId);
    }

    @Test
    void assinaturasDevemDiferirEntreIngressos() {
        String first = signer.sign(UUID.randomUUID());
        String second = signer.sign(UUID.randomUUID());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void deveRecusarCodigoSemPonto() {
        assertThatThrownBy(() -> signer.verifyAndExtract("codigo-sem-ponto"))
                .isInstanceOf(br.com.portaria.shared.exception.InvalidTicketCodeException.class);
    }

    /** O PNG precisa ser um QR de verdade: leio de volta o que foi desenhado. */
    @Test
    void devePintarUmQrCodeLegivelDe300x300() throws Exception {
        String code = signer.sign(UUID.randomUUID());

        byte[] png = renderer.toPng(code);
        var image = ImageIO.read(new ByteArrayInputStream(png));

        assertThat(image.getWidth()).isEqualTo(300);
        assertThat(image.getHeight()).isEqualTo(300);

        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        assertThat(new QRCodeReader().decode(bitmap).getText()).isEqualTo(code);
    }
}
