package com.community.highlight.service;

import com.community.highlight.entity.HighlightEntity;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 하이라이트가 놓일 곳(V161 {@code highlights.place}) — docs/features/tips-contract.md 「결정」.
 * 곳 = {@value #HOME}(홈, 기본 — 예전 하이라이트 전부) · {@value #TIPS}(팁·강좌·사용기 「이번 주 추천」) · 가젯 페이지 키. 키 모양은 가젯 페이지 키 규칙과 같다.
 * 가젯 페이지 키가 {@code home}·{@code tips} 와 같으면 같은 곳으로 본다(같은 목록을 쓴다).
 */
public final class HighlightPlaces {

    public static final String HOME = "home";
    public static final String TIPS = "tips";
    /** 곳 키 모양 — 가젯 페이지 키({@code GadgetPageAdminService}) 와 같다 */
    public static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9-]{1,29}$");

    private HighlightPlaces() {
    }

    /** 늘 있는 곳(가젯 페이지가 아닌 곳)인가 */
    public static boolean builtIn(String key) {
        return HOME.equals(key) || TIPS.equals(key);
    }

    /** 모양이 맞는 키인가 */
    public static boolean wellFormed(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    /**
     * 요청·설정 값 → 곳 키. 비었으면 {@value #HOME}, 앞뒤 공백은 뗀다. 모양이 틀리면 빈 값(부르는 쪽이 오류 또는 {@value #HOME} 으로 정한다).
     */
    public static Optional<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(HOME);
        }
        String key = raw.strip();
        return wellFormed(key) ? Optional.of(key) : Optional.empty();
    }

    /** 한 글의 여러 하이라이트 중 대표 하나 — 홈 것이 먼저, 없으면 먼저 만든 것 */
    public static Optional<HighlightEntity> primary(Collection<HighlightEntity> rows) {
        return rows.stream().min(Comparator.comparing((HighlightEntity h) -> !HOME.equals(h.getPlace())).thenComparing(HighlightEntity::getId));
    }
}
