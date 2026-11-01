package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.og.DealOgImageEncoder;
import com.community.media.config.MediaProperties;
import com.community.media.service.ImageCodec;
import com.community.media.service.ImageFormat;
import com.community.media.service.ImageGate;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * D1 OG 사본 형식(2026-11-01 서버 단계 결정) — 가로 600px JPEG(늘이지 않음) · ≤300KB 가 되도록 품질을 낮춤 · 투명은 흰 바탕 · WebP 는 ≤300KB 그리고 가로 ≤1200 일 때만
 * 메타데이터만 떼어 그대로, 넘으면 이미지 없음 · 그림이 아니면 이미지 없음.
 */
class DealOgImageEncoderTest {

    private final DealOgImageEncoder encoder = new DealOgImageEncoder(new ImageCodec(new ImageGate(new MediaProperties(2, 10, 24))));

    @Test
    void 넓은_PNG_는_가로_600_JPEG_로_줄이고_투명은_흰_바탕() throws IOException {
        BufferedImage png = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_ARGB); // 전부 투명
        Graphics2D g = png.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.dispose();
        DealOgImageEncoder.Encoded out = encoder.encode(bytes(png, "png"));

        assertThat(out).isNotNull();
        assertThat(out.format()).isEqualTo(ImageFormat.JPEG);
        assertThat(ImageFormat.sniff(out.data())).isEqualTo(ImageFormat.JPEG);
        assertThat(out.width()).isEqualTo(600);
        assertThat(out.height()).isEqualTo(400);
        assertThat(out.data().length).isLessThanOrEqualTo(DealOgImageEncoder.MAX_BYTES);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(out.data()));
        int rgb = decoded.getRGB(500, 300); // 투명했던 자리
        assertThat((rgb >> 16) & 0xff).isGreaterThan(245);
        assertThat((rgb >> 8) & 0xff).isGreaterThan(245);
        assertThat(rgb & 0xff).isGreaterThan(245);
    }

    @Test
    void 작은_그림은_늘이지_않는다() throws IOException {
        BufferedImage jpg = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        DealOgImageEncoder.Encoded out = encoder.encode(bytes(jpg, "jpg"));
        assertThat(out).isNotNull();
        assertThat(out.width()).isEqualTo(320);
        assertThat(out.height()).isEqualTo(200);
    }

    @Test
    void 무늬가_많은_그림도_품질을_낮춰_300KB_안으로() throws IOException {
        BufferedImage noise = new BufferedImage(2400, 2400, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7);
        for (int y = 0; y < noise.getHeight(); y++) {
            for (int x = 0; x < noise.getWidth(); x++) {
                noise.setRGB(x, y, random.nextInt(0xffffff));
            }
        }
        byte[] source = bytes(noise, "png");
        assertThat(source.length).isGreaterThan(DealOgImageEncoder.MAX_BYTES);
        DealOgImageEncoder.Encoded out = encoder.encode(source);
        assertThat(out).isNotNull();
        assertThat(out.width()).isEqualTo(600);
        assertThat(out.data().length).isLessThanOrEqualTo(DealOgImageEncoder.MAX_BYTES);
    }

    @Test
    void WebP_는_300KB_이하_그리고_가로_1200_이하일_때만_그대로() {
        DealOgImageEncoder.Encoded ok = encoder.encode(webp(1200, 630, 10_000));
        assertThat(ok).isNotNull();
        assertThat(ok.format()).isEqualTo(ImageFormat.WEBP);
        assertThat(ok.width()).isEqualTo(1200);

        assertThat(encoder.encode(webp(1201, 630, 10_000))).as("가로 1200 넘음").isNull();
        assertThat(encoder.encode(webp(800, 600, DealOgImageEncoder.MAX_BYTES + 1))).as("300KB 넘음").isNull();
    }

    @Test
    void WebP_는_메타데이터_청크를_뗀다() {
        byte[] withExif = webpWithExif(640, 480);
        DealOgImageEncoder.Encoded out = encoder.encode(withExif);
        assertThat(out).isNotNull();
        assertThat(new String(out.data(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("EXIF");
    }

    @Test
    void 그림이_아니면_이미지_없음() {
        assertThat(encoder.encode("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes())).isNull();
        assertThat(encoder.encode(new byte[0])).isNull();
        assertThat(encoder.encode(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3})).as("깨진 PNG").isNull();
    }

    private static byte[] bytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    /** VP8L(무손실) 머리만 맞춘 WebP — 크기 표시와 길이만 본다(디코딩하지 않는다) */
    static byte[] webp(int width, int height, int totalBytes) {
        int payload = Math.max(5, totalBytes - 20);
        if ((payload & 1) == 1) {
            payload++;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "RIFF");
        le32(out, 4 + 8 + payload);
        writeAscii(out, "WEBP");
        writeAscii(out, "VP8L");
        le32(out, payload);
        out.write(0x2f);
        long bits = (width - 1L) | ((height - 1L) << 14);
        le32(out, bits);
        for (int i = 5; i < payload; i++) {
            out.write(0);
        }
        return out.toByteArray();
    }

    private static byte[] webpWithExif(int width, int height) {
        byte[] exif = "EXIF-DATA-123456".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int vp8x = 10;
        int vp8l = 6;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "RIFF");
        le32(out, 4 + (8 + vp8x) + (8 + vp8l) + (8 + exif.length));
        writeAscii(out, "WEBP");
        writeAscii(out, "VP8X");
        le32(out, vp8x);
        out.write(0x08); // EXIF 있음 표시
        out.write(0);
        out.write(0);
        out.write(0);
        le24(out, width - 1);
        le24(out, height - 1);
        writeAscii(out, "VP8L");
        le32(out, vp8l);
        out.write(0x2f);
        le32(out, (width - 1L) | ((height - 1L) << 14));
        out.write(0);
        writeAscii(out, "EXIF");
        le32(out, exif.length);
        out.writeBytes(exif);
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static void le32(ByteArrayOutputStream out, long v) {
        out.write((int) (v & 0xff));
        out.write((int) ((v >> 8) & 0xff));
        out.write((int) ((v >> 16) & 0xff));
        out.write((int) ((v >> 24) & 0xff));
    }

    private static void le24(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
        out.write((v >> 16) & 0xff);
    }
}
