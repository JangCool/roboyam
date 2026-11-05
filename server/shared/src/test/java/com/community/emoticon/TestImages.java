package com.community.emoticon;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/**
 * 시험용 그림 바이트 — 손으로 짠 GIF(논리 화면 크기 · 프레임 수 · 무한 반복 · 주석 덧붙임으로 지문·용량 조절)와 ImageIO 로 만든 PNG, 그리고 두 앱 public 의 로보얌콘 파일.
 * GIF 프레임은 1×1 그림(잘 알려진 최소 LZW 데이터 {@code 02 02 44 01 00})이라 ImageIO 가 실제로 디코딩한다.
 */
final class TestImages {

    /** 시험 작업 폴더 = server/shared → 저장소 맨 위 */
    static final Path REPO = Paths.get("").toAbsolutePath().getParent().getParent();

    private TestImages() {
    }

    /** GIF 를 짠다 */
    static final class Gif {
        int width = 200;
        int height = 200;
        int frames = 1;
        /** null = NETSCAPE 블록 없음(여러 프레임이면 한 번만 재생) */
        Integer loop = 0;
        /** 주석 확장 블록 글자(지문을 바꾼다) */
        String comment = "";
        /** 주석 확장 블록에 더할 바이트 수(용량을 키운다 — 그림 안이라 저장할 바이트에 남는다) */
        int padding = 0;
        /** 첫 프레임의 그림 크기(기본 1×1 — 논리 화면 밖으로 적으면 폭탄) */
        int frameWidth = 1;
        int frameHeight = 1;
        /** 끝 표식 뒤에 붙일 바이트 수 */
        int trailing = 0;

        Gif size(int w, int h) {
            width = w;
            height = h;
            return this;
        }

        Gif frames(int n) {
            frames = n;
            return this;
        }

        Gif loop(Integer count) {
            loop = count;
            return this;
        }

        Gif comment(String text) {
            comment = text;
            return this;
        }

        Gif padding(int bytes) {
            padding = bytes;
            return this;
        }

        Gif frame(int w, int h) {
            frameWidth = w;
            frameHeight = h;
            return this;
        }

        Gif trailing(int bytes) {
            trailing = bytes;
            return this;
        }

        byte[] bytes() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes("GIF89a".getBytes(StandardCharsets.US_ASCII));
            le16(out, width);
            le16(out, height);
            out.write(0x80); // 전체 색표 있음, 2색
            out.write(0);
            out.write(0);
            out.writeBytes(new byte[] {0, 0, 0, (byte) 0xff, (byte) 0xff, (byte) 0xff});
            if (loop != null) {
                out.write(0x21);
                out.write(0xff);
                out.write(11);
                out.writeBytes("NETSCAPE2.0".getBytes(StandardCharsets.US_ASCII));
                out.write(3);
                out.write(1);
                le16(out, loop);
                out.write(0);
            }
            byte[] text = (comment + "x".repeat(padding)).getBytes(StandardCharsets.US_ASCII);
            if (text.length > 0) {
                out.write(0x21);
                out.write(0xfe);
                for (int i = 0; i < text.length; i += 255) {
                    int n = Math.min(255, text.length - i);
                    out.write(n);
                    out.write(text, i, n);
                }
                out.write(0);
            }
            for (int f = 0; f < frames; f++) {
                out.writeBytes(new byte[] {0x21, (byte) 0xf9, 4, 0, 10, 0, 0, 0}); // 그래픽 제어(지연 0.1초)
                out.write(0x2c);
                le16(out, 0);
                le16(out, 0);
                le16(out, f == 0 ? frameWidth : 1);
                le16(out, f == 0 ? frameHeight : 1);
                out.write(0);
                out.writeBytes(new byte[] {2, 2, 0x44, 1, 0});
            }
            out.write(0x3b);
            out.writeBytes(new byte[trailing]);
            return out.toByteArray();
        }

        private static void le16(ByteArrayOutputStream out, int v) {
            out.write(v & 0xff);
            out.write((v >> 8) & 0xff);
        }
    }

    static Gif gif() {
        return new Gif();
    }

    /** 움직이는(2프레임) 무한 반복 200×200 GIF — 글자로 지문을 가른다 */
    static byte[] animated(String tag) {
        return gif().frames(2).comment(tag).bytes();
    }

    /** {@code w}×{@code h} PNG — 색으로 지문을 가른다. {@code trailing} 은 IEND 뒤에 붙일 바이트 수 */
    static byte[] png(int w, int h, int rgb, int trailing) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(w / 2, h / 2, new Color(rgb, false).getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.writeBytes(new byte[trailing]);
        return out.toByteArray();
    }

    static byte[] cover(int rgb) {
        return png(200, 200, rgb, 0);
    }

    /** 두 앱 public 의 로보얌콘 파일({@code robotikon-m/01.gif} 등) */
    static byte[] robotikon(String file) {
        try {
            return Files.readAllBytes(REPO.resolve("apps/user-web/public/emoticons").resolve(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
