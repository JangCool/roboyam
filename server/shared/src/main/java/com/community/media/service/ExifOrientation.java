package com.community.media.service;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

/**
 * JPEG 의 EXIF 방향(Orientation, 태그 0x0112)을 읽어 그림에 반영한다 — 다시 인코딩하면 EXIF 가 사라지므로(메타데이터 제거) 휴대폰 사진이 눕지 않게
 * 미리 돌려 둔다. 라이브러리 없이 APP1 「Exif」 조각의 IFD0 만 읽는다. 읽을 수 없거나 없으면 1(그대로).
 */
public final class ExifOrientation {

    private ExifOrientation() {
    }

    /** 방향 값 1~8(모르면 1) */
    public static int read(byte[] d) {
        try {
            return parse(d);
        } catch (RuntimeException e) {
            return 1;
        }
    }

    private static int parse(byte[] d) {
        if (d == null || d.length < 4 || (d[0] & 0xff) != 0xff || (d[1] & 0xff) != 0xd8) {
            return 1;
        }
        int pos = 2;
        while (pos + 4 <= d.length) {
            if ((d[pos] & 0xff) != 0xff) {
                return 1;
            }
            int marker = d[pos + 1] & 0xff;
            if (marker == 0xd8 || marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7)) {
                pos += 2;
                continue;
            }
            if (marker == 0xda || marker == 0xd9) {
                return 1; // 그림 데이터가 시작됐다 — 그 앞에 EXIF 가 없었다
            }
            int length = u16(d, pos + 2, false);
            int end = pos + 2 + length;
            if (length < 2 || end > d.length) {
                return 1;
            }
            if (marker == 0xe1 && length >= 16 && "Exif".equals(new String(d, pos + 4, 4, StandardCharsets.US_ASCII)) && d[pos + 8] == 0 && d[pos + 9] == 0) {
                return fromTiff(d, pos + 10, end);
            }
            pos = end;
        }
        return 1;
    }

    private static int fromTiff(byte[] d, int tiff, int end) {
        boolean little;
        if (d[tiff] == 'I' && d[tiff + 1] == 'I') {
            little = true;
        } else if (d[tiff] == 'M' && d[tiff + 1] == 'M') {
            little = false;
        } else {
            return 1;
        }
        if (u16(d, tiff + 2, little) != 42) {
            return 1;
        }
        long ifd = u32(d, tiff + 4, little);
        int at = (int) (tiff + ifd);
        if (ifd < 8 || at + 2 > end) {
            return 1;
        }
        int entries = u16(d, at, little);
        for (int i = 0; i < entries; i++) {
            int entry = at + 2 + i * 12;
            if (entry + 12 > end) {
                return 1;
            }
            if (u16(d, entry, little) == 0x0112) {
                int value = u16(d, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    /** 방향에 맞게 돌리거나 뒤집은 새 그림(불투명 RGB). 1 이면 그대로 */
    public static BufferedImage apply(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        // (m00, m10, m01, m11, m02, m12): x' = m00·x + m01·y + m02, y' = m10·x + m11·y + m12
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // 좌우 뒤집기
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // 180°
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // 위아래 뒤집기
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // 대각선 뒤집기
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // 시계 방향 90°
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // 반대 대각선 뒤집기
            default -> new AffineTransform(0, -1, 1, 0, 0, w);  // 8: 반시계 방향 90°
        };
        boolean swap = orientation >= 5;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(image, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static int u16(byte[] d, int at, boolean little) {
        return little ? (d[at] & 0xff) | ((d[at + 1] & 0xff) << 8) : ((d[at] & 0xff) << 8) | (d[at + 1] & 0xff);
    }

    private static long u32(byte[] d, int at, boolean little) {
        return little
                ? (d[at] & 0xffL) | ((d[at + 1] & 0xffL) << 8) | ((d[at + 2] & 0xffL) << 16) | ((d[at + 3] & 0xffL) << 24)
                : ((d[at] & 0xffL) << 24) | ((d[at + 1] & 0xffL) << 16) | ((d[at + 2] & 0xffL) << 8) | (d[at + 3] & 0xffL);
    }
}
