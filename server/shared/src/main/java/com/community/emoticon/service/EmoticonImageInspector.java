package com.community.emoticon.service;

import com.community.emoticon.entity.EmoticonImageFormat;
import java.nio.charset.StandardCharsets;

/**
 * 그림 파일의 <b>바이트</b>를 훑어 형식·크기·프레임·무한 반복을 읽는다 — 디코딩하지 않는다(그림 데이터를 풀기 전에 크기를 알아 압축 폭탄을 막는다). 파일 이름·Content-Type 은 보지 않는다.
 * user-web {@code util-emoticon-spec.ts} · admin-web {@code util-emoticon-image.ts} 의 읽기와 같은 방식이다(두 앱 · 서버가 같은 값을 보게 — 바꿀 때 함께):
 * <ul>
 *   <li>GIF: 머리(논리 화면 크기) → 전체 색표 → 블록을 따라간다. 확장 블록({@code 0x21})은 부분 블록을 건너뛰며 NETSCAPE2.0 이면 반복 횟수를 읽고, 이미지 블록({@code 0x2C})을 하나씩
 *       센다(그림 데이터 속 같은 값은 부분 블록째 건너뛰므로 세지 않는다). 끝 표식({@code 0x3B}) 뒤의 바이트는 그림이 아니다({@link Info#end}).
 *       서버만 더 보는 것(화면은 보지 않는다): 이미지 블록의 위치·크기가 논리 화면 안에 있어야 하고(밖으로 크게 적은 프레임 — 디코딩 폭탄), 끝 표식이 있어야 한다(잘린 파일).</li>
 *   <li>PNG: 서명 + IHDR 의 가로·세로. 프레임 1 · 무한 반복으로 본다.</li>
 * </ul>
 * 순수 함수 — 상태가 없다.
 */
public final class EmoticonImageInspector {

    /**
     * 읽은 그림 정보.
     *
     * @param frames      GIF 프레임 수(PNG 는 1)
     * @param loopForever GIF 무한 반복(NETSCAPE2.0 반복 0) — 한 프레임 GIF·PNG 는 참
     * @param end         그림이 끝나는 자리(GIF 끝 표식 다음 — 뒤에 붙은 바이트는 저장하지 않는다. PNG 는 파일 길이)
     */
    public record Info(EmoticonImageFormat format, int width, int height, int frames, boolean loopForever, int end) {
    }

    private EmoticonImageInspector() {
    }

    /** GIF·PNG 가 아니거나 깨졌으면 null */
    public static Info inspect(byte[] b) {
        if (b == null || b.length < 6) {
            return null;
        }
        String head = new String(b, 0, 6, StandardCharsets.ISO_8859_1);
        if (head.equals("GIF87a") || head.equals("GIF89a")) {
            return gif(b);
        }
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0d && b[5] == 0x0a && b[6] == 0x1a && b[7] == 0x0a) {
            return png(b);
        }
        return null;
    }

    private static int u8(byte[] b, int at) {
        return at < b.length ? b[at] & 0xff : 0;
    }

    private static int u16le(byte[] b, int at) {
        return u8(b, at) | (u8(b, at + 1) << 8);
    }

    private static long u32be(byte[] b, int at) {
        return ((long) u8(b, at) << 24) | ((long) u8(b, at + 1) << 16) | ((long) u8(b, at + 2) << 8) | u8(b, at + 3);
    }

    /** GIF 부분 블록 묶음(크기 바이트 + 데이터 … 0)을 건너뛴 다음 자리. 끝을 넘으면 -1 */
    private static int skipSubBlocks(byte[] b, int at) {
        int i = at;
        while (i < b.length) {
            int size = u8(b, i);
            i += 1;
            if (size == 0) {
                return i;
            }
            i += size;
        }
        return -1;
    }

    private static Info gif(byte[] b) {
        if (b.length < 13) {
            return null;
        }
        int width = u16le(b, 6);
        int height = u16le(b, 8);
        int packed = u8(b, 10);
        int i = 13;
        if ((packed & 0x80) != 0) {
            i += 3 * (1 << ((packed & 0x07) + 1)); // 전체 색표
        }
        int frames = 0;
        Integer loopCount = null;
        while (i < b.length) {
            int marker = u8(b, i);
            if (marker == 0x3b) {
                if (frames == 0) {
                    return null;
                }
                return new Info(EmoticonImageFormat.GIF, width, height, frames, frames <= 1 || Integer.valueOf(0).equals(loopCount), i + 1);
            }
            if (marker == 0x21) {
                int label = u8(b, i + 1);
                int size = u8(b, i + 2);
                if (label == 0xff && size == 11 && i + 14 <= b.length) {
                    String id = new String(b, i + 3, 11, StandardCharsets.ISO_8859_1);
                    if (id.equals("NETSCAPE2.0") && u8(b, i + 14) == 3 && u8(b, i + 15) == 1) {
                        loopCount = u16le(b, i + 16);
                    }
                }
                int next = skipSubBlocks(b, i + 2);
                if (next < 0) {
                    return null;
                }
                i = next;
                continue;
            }
            if (marker == 0x2c) {
                // 이미지 블록 — 머리 10바이트(위치·크기·묶음 값) · (지역 색표) · LZW 최소 코드 크기 1바이트 · 데이터 부분 블록
                int left = u16le(b, i + 1);
                int top = u16le(b, i + 3);
                int w = u16le(b, i + 5);
                int h = u16le(b, i + 7);
                if (w == 0 || h == 0 || left + w > width || top + h > height) {
                    return null; // 논리 화면 밖으로 적은 프레임 — 풀면 커지는 폭탄이거나 깨진 파일
                }
                frames += 1;
                int localPacked = u8(b, i + 9);
                i += 10;
                if ((localPacked & 0x80) != 0) {
                    i += 3 * (1 << ((localPacked & 0x07) + 1));
                }
                i += 1;
                int next = skipSubBlocks(b, i);
                if (next < 0) {
                    return null;
                }
                i = next;
                continue;
            }
            return null; // 모르는 표식 — 깨진 파일
        }
        return null; // 끝 표식이 없다 — 잘린 파일
    }

    private static Info png(byte[] b) {
        // 서명 8바이트 + IHDR(길이 4 · "IHDR" 4 · 가로 4 · 세로 4)
        if (b.length < 24 || !new String(b, 12, 4, StandardCharsets.ISO_8859_1).equals("IHDR")) {
            return null;
        }
        long width = u32be(b, 16);
        long height = u32be(b, 20);
        if (width <= 0 || height <= 0 || width > Integer.MAX_VALUE || height > Integer.MAX_VALUE) {
            return null;
        }
        return new Info(EmoticonImageFormat.PNG, (int) width, (int) height, 1, true, b.length);
    }
}
