package com.community.pointshop;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/** 올리기 시험용 그림 바이트(PNG·JPG·여러 장면 GIF·WebP 머리) — 파일 내용으로 판정하는 서버 규칙을 시험한다 */
final class TestImages {

    private TestImages() {
    }

    static BufferedImage image(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    static byte[] png(int width, int height) {
        return write(image(width, height, Color.ORANGE), "png");
    }

    static byte[] jpeg(int width, int height) {
        return write(image(width, height, Color.BLUE), "jpg");
    }

    /** 압축이 안 되는 무늬 PNG — 크기 한도 시험 */
    static byte[] noisePng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        return write(image, "png");
    }

    /** 장면 {@code frames} 개짜리 GIF */
    static byte[] gif(int frames, int width, int height) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frames; i++) {
                BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED);
                var g = frame.createGraphics();
                g.setColor(i % 2 == 0 ? Color.RED : Color.GREEN);
                g.fillRect(0, 0, width, height);
                g.dispose();
                writer.writeToSequence(new IIOImage(frame, null, null), null);
            }
            writer.endWriteSequence();
            writer.dispose();
            ios.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 정지 WebP(VP8L 머리만 — 서버는 디코딩하지 않고 컨테이너만 본다) */
    static byte[] webpStill(int width, int height) {
        long bits = (width - 1) | ((long) (height - 1) << 14);
        byte[] payload = {0x2f, (byte) bits, (byte) (bits >> 8), (byte) (bits >> 16), (byte) (bits >> 24), 0, 0, 0};
        return riff(chunk("VP8L", payload));
    }

    /** 움직이는 WebP(VP8X 애니메이션 표시 + ANMF) */
    static byte[] webpAnimated(int width, int height) {
        byte[] vp8x = new byte[10];
        vp8x[0] = 0x02;
        le24(vp8x, 4, width - 1);
        le24(vp8x, 7, height - 1);
        byte[] anim = new byte[6];
        byte[] anmf = new byte[16];
        return riff(concat(chunk("VP8X", vp8x), chunk("ANIM", anim), chunk("ANMF", anmf)));
    }

    private static byte[] riff(byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes());
        le32(out, body.length + 4);
        out.writeBytes("WEBP".getBytes());
        out.writeBytes(body);
        return out.toByteArray();
    }

    private static byte[] chunk(String id, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(id.getBytes());
        le32(out, payload.length);
        out.writeBytes(payload);
        if ((payload.length & 1) == 1) {
            out.write(0);
        }
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static void le32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private static void le24(byte[] d, int at, int value) {
        d[at] = (byte) value;
        d[at + 1] = (byte) (value >> 8);
        d[at + 2] = (byte) (value >> 16);
    }

    private static byte[] write(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
