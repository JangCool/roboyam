package com.community.media.service;

import com.community.media.exception.ImageUnreadableException;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * WebP 파일의 구조(RIFF 컨테이너)를 읽고 정리한다. JDK 에는 WebP 디코더가 없고 라이브러리를 더하지 않으므로 <b>그림 자체를 다시 인코딩하지 않는다</b> —
 * 대신 컨테이너를 검사해(크기가 맞는 RIFF·알려진 첫 청크) 가로·세로를 읽고, 메타데이터({@code EXIF}·{@code XMP })와 모르는 청크·RIFF 뒤에 붙은 데이터를 떼어 다시 짠다.
 * 그림 데이터({@code VP8 }·{@code VP8L}·{@code ALPH}·애니메이션 프레임)는 그대로 둔다 — 색 프로파일({@code ICCP})은 그림이 달라 보이지 않게 남긴다.
 */
final class WebpContainer {

    /** 읽은 가로·세로(px) */
    record Info(int width, int height) {
    }

    private record Chunk(String id, byte[] payload) {
    }

    private WebpContainer() {
    }

    /** 컨테이너를 검사하고 가로·세로를 읽는다. 읽을 수 없으면 {@link ImageUnreadableException} */
    static Info inspect(byte[] data) {
        return parse(data).info();
    }

    /** 메타데이터·모르는 청크·뒤에 붙은 데이터를 뗀 새 파일. 읽을 수 없으면 {@link ImageUnreadableException} */
    static byte[] clean(byte[] data) {
        Parsed parsed = parse(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        int bodySize = 4;
        List<Chunk> kept = new ArrayList<>();
        for (Chunk chunk : parsed.chunks()) {
            if (!isKept(chunk.id())) {
                continue;
            }
            Chunk next = chunk;
            if (chunk.id().equals("VP8X")) {
                byte[] payload = chunk.payload().clone();
                payload[0] = (byte) (payload[0] & ~(0x08 | 0x04)); // EXIF·XMP 청크를 떼므로 표시도 끈다
                next = new Chunk("VP8X", payload);
            }
            kept.add(next);
            bodySize += 8 + next.payload().length + (next.payload().length & 1);
        }
        out.writeBytes(new byte[] {'R', 'I', 'F', 'F'});
        writeLe32(out, bodySize);
        out.writeBytes(new byte[] {'W', 'E', 'B', 'P'});
        for (Chunk chunk : kept) {
            out.writeBytes(chunk.id().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            writeLe32(out, chunk.payload().length);
            out.writeBytes(chunk.payload());
            if ((chunk.payload().length & 1) == 1) {
                out.write(0);
            }
        }
        return out.toByteArray();
    }

    private static boolean isKept(String id) {
        return switch (id) {
            case "VP8X", "ICCP", "ALPH", "VP8 ", "VP8L", "ANIM", "ANMF" -> true;
            default -> false;
        };
    }

    private record Parsed(Info info, List<Chunk> chunks) {
    }

    private static Parsed parse(byte[] d) {
        if (d.length < 20 || d[0] != 'R' || d[1] != 'I' || d[2] != 'F' || d[3] != 'F' || d[8] != 'W' || d[9] != 'E' || d[10] != 'B' || d[11] != 'P') {
            throw new ImageUnreadableException("WebP 서명이 아니다");
        }
        long riffSize = le32(d, 4);
        long end = 8 + riffSize;
        if (riffSize < 12 || end > d.length) {
            throw new ImageUnreadableException("WebP 크기 표시가 파일보다 크다");
        }
        List<Chunk> chunks = new ArrayList<>();
        int pos = 12;
        while (pos + 8 <= end) {
            String id = new String(d, pos, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            long size = le32(d, pos + 4);
            long payloadEnd = pos + 8 + size;
            if (payloadEnd > end) {
                throw new ImageUnreadableException("WebP 청크가 파일 끝을 넘는다");
            }
            byte[] payload = new byte[(int) size];
            System.arraycopy(d, pos + 8, payload, 0, (int) size);
            chunks.add(new Chunk(id, payload));
            pos = (int) (payloadEnd + (size & 1));
        }
        if (chunks.isEmpty()) {
            throw new ImageUnreadableException("WebP 에 청크가 없다");
        }
        Chunk first = chunks.get(0);
        Info info;
        switch (first.id()) {
            case "VP8X" -> {
                byte[] p = first.payload();
                if (p.length < 10) {
                    throw new ImageUnreadableException("VP8X 청크가 짧다");
                }
                info = new Info(le24(p, 4) + 1, le24(p, 7) + 1);
            }
            case "VP8 " -> {
                byte[] p = first.payload();
                if (p.length < 10 || (p[3] & 0xff) != 0x9d || (p[4] & 0xff) != 0x01 || (p[5] & 0xff) != 0x2a) {
                    throw new ImageUnreadableException("VP8 프레임 머리가 아니다");
                }
                info = new Info(((p[6] & 0xff) | ((p[7] & 0xff) << 8)) & 0x3fff, ((p[8] & 0xff) | ((p[9] & 0xff) << 8)) & 0x3fff);
            }
            case "VP8L" -> {
                byte[] p = first.payload();
                if (p.length < 5 || (p[0] & 0xff) != 0x2f) {
                    throw new ImageUnreadableException("VP8L 서명이 아니다");
                }
                long bits = (p[1] & 0xffL) | ((p[2] & 0xffL) << 8) | ((p[3] & 0xffL) << 16) | ((p[4] & 0xffL) << 24);
                info = new Info((int) (bits & 0x3fff) + 1, (int) ((bits >> 14) & 0x3fff) + 1);
            }
            default -> throw new ImageUnreadableException("WebP 첫 청크를 알 수 없다");
        }
        if (info.width() <= 0 || info.height() <= 0) {
            throw new ImageUnreadableException("WebP 크기가 0 이다");
        }
        if (chunks.stream().noneMatch(c -> c.id().equals("VP8 ") || c.id().equals("VP8L") || c.id().equals("ANMF"))) {
            throw new ImageUnreadableException("WebP 에 그림 데이터가 없다");
        }
        return new Parsed(info, chunks);
    }

    private static long le32(byte[] d, int at) {
        return (d[at] & 0xffL) | ((d[at + 1] & 0xffL) << 8) | ((d[at + 2] & 0xffL) << 16) | ((d[at + 3] & 0xffL) << 24);
    }

    private static int le24(byte[] d, int at) {
        return (d[at] & 0xff) | ((d[at + 1] & 0xff) << 8) | ((d[at + 2] & 0xff) << 16);
    }

    private static void writeLe32(ByteArrayOutputStream out, long value) {
        out.write((int) (value & 0xff));
        out.write((int) ((value >> 8) & 0xff));
        out.write((int) ((value >> 16) & 0xff));
        out.write((int) ((value >> 24) & 0xff));
    }
}
