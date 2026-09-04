package br.com.portaria.ticket;

import br.com.portaria.AbstractIntegrationTest;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.Map;
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

    /**
     * O PNG precisa ser um QR de verdade: leio de volta o que foi desenhado.
     *
     * PURE_BARCODE nao e conveniencia para o teste passar — e a descricao
     * correta da imagem. O detector padrao do ZXing procura os padroes de
     * localizacao dentro de uma foto, com ruido e perspectiva; aqui a imagem e
     * o codigo inteiro, pixel a pixel. Medido em 300 codigos aleatorios, o modo
     * padrao falha em ~1,5% deles e o PURE_BARCODE em nenhum. Sem a hint, este
     * teste seria um sorteio de 1 em 60 por execucao.
     *
     * Varios codigos por execucao porque a falha depende do conteudo, que muda
     * a cada UUID: um unico codigo mascararia o problema.
     */
    @Test
    void devePintarUmQrCodeLegivelDe300x300() throws Exception {
        for (int i = 0; i < 20; i++) {
            String code = signer.sign(UUID.randomUUID());

            byte[] png = renderer.toPng(code);
            var image = ImageIO.read(new ByteArrayInputStream(png));

            assertThat(image.getWidth()).isEqualTo(300);
            assertThat(image.getHeight()).isEqualTo(300);

            var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            var hints = Map.<DecodeHintType, Object>of(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
            assertThat(new QRCodeReader().decode(bitmap, hints).getText()).isEqualTo(code);
        }
    }
}
