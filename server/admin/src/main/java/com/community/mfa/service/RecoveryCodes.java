package com.community.mfa.service;

import com.community.zconfiguration.crypto.Digests;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 복구 코드 — 10개, {@code XXXXX-XXXXX}(헷갈리는 글자 0·O·1·I 를 뺀 대문자·숫자 32자), 받을 때는 대소문자·하이픈·공백을 무시한다.
 * DB 에는 SHA-256(운영자 ID ‖ {@code :} ‖ 정규화한 코드)만 둔다 — 운영자마다 해시가 달라 같은 코드가 다른 운영자의 행과 엮이지 않는다. 상태가 없는 순수 함수다.
 * 코드를 로그·감사·오류에 싣지 않는다.
 */
public final class RecoveryCodes {

    public static final int COUNT = 10;
    /** 하이픈을 뺀 길이 */
    public static final int LENGTH = 10;
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private RecoveryCodes() {
    }

    /** 서로 다른 새 코드 10개({@code XXXXX-XXXXX}) */
    public static List<String> generate() {
        Set<String> codes = new LinkedHashSet<>();
        while (codes.size() < COUNT) {
            StringBuilder raw = new StringBuilder(LENGTH + 1);
            for (int i = 0; i < LENGTH; i++) {
                if (i == LENGTH / 2) {
                    raw.append('-');
                }
                raw.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            codes.add(raw.toString());
        }
        return new ArrayList<>(codes);
    }

    /** 대문자로 바꾸고 하이픈·공백을 뺀 10자. 형식이 아니면(길이·글자) 비어 있다 */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String value = input.replace("-", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (value.length() != LENGTH) {
            return Optional.empty();
        }
        for (char c : value.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(value);
    }

    /** 저장·대조용 해시 — {@code normalized} 는 {@link #normalize} 를 거친 값 */
    public static String hash(long operatorId, String normalized) {
        return Digests.sha256Hex(operatorId + ":" + normalized);
    }
}
