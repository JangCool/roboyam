package com.community.mfa.totp;

import java.io.ByteArrayOutputStream;

/**
 * RFC 4648 base32(알파벳 {@code A–Z 2–7}) — OTP 앱이 읽는 비밀키 표기다. 쓸 때는 대문자·채움 문자({@code =}) 없이, 읽을 때는 대소문자·공백·채움 문자를 무시한다.
 * 상태가 없는 순수 함수다.
 */
public final class Base32 {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Base32() {
    }

    public static String encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET[(buffer >> (bits - 5)) & 0x1f]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(ALPHABET[(buffer << (5 - bits)) & 0x1f]);
        }
        return out.toString();
    }

    /**
     * @throws IllegalArgumentException base32 가 아닌 글자가 있을 때(값은 오류 문구에 싣지 않는다)
     */
    public static byte[] decode(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char raw : text.toCharArray()) {
            if (raw == '=' || raw == ' ' || raw == '-') {
                continue;
            }
            char c = Character.toUpperCase(raw);
            int value;
            if (c >= 'A' && c <= 'Z') {
                value = c - 'A';
            } else if (c >= '2' && c <= '7') {
                value = c - '2' + 26;
            } else {
                throw new IllegalArgumentException("base32 가 아닌 글자가 있다");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
