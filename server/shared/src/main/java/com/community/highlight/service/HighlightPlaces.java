package com.community.highlight.service;

import com.community.highlight.entity.HighlightEntity;
import com.community.home.entity.GadgetPageEntity;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 하이라이트가 놓일 곳(V161 {@code highlights.place}) — docs/features/tips-contract.md 「결정」.
 * 곳 = {@value #HOME}(홈, 기본 — 예전 하이라이트 전부) · {@value #TIPS}(팁·강좌·사용기 「이번 주 추천」) · 가젯 페이지 키 · 게시판 곳 {@code board-{boardId}}
 * (게시판 목록 위 「추천 글」 — docs/features/board-recommend-contract.md). 키 모양은 가젯 페이지 키 규칙과 같다.
 * 가젯 페이지 키가 {@code home}·{@code tips} 와 같으면 같은 곳으로 본다(같은 목록을 쓴다). {@value #BOARD_PREFIX} 로 시작하는 키는 게시판 곳 전용이라 가젯 페이지 키로 쓰지 못한다.
 */
public final class HighlightPlaces {

    public static final String HOME = "home";
    public static final String TIPS = "tips";
    /** 게시판 곳 키의 앞 — 뒤에 게시판 ID(숫자) */
    public static final String BOARD_PREFIX = "board-";
    /** 게시판 곳이 공개에 보이는 최대 카드 수(고른 글 → 자동 채움) */
    public static final int BOARD_SHOWN = 3;
    /** 곳 키 모양 — 가젯 페이지 키({@code GadgetPageAdminService}) 와 같다 */
    public static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9-]{1,29}$");

    private HighlightPlaces() {
    }

    /** 늘 있는 곳(가젯 페이지가 아닌 곳)인가 */
    public static boolean builtIn(String key) {
        return HOME.equals(key) || TIPS.equals(key);
    }

    /** 게시판 곳 몫의 키인가({@value #BOARD_PREFIX} 로 시작 — 뒤가 숫자가 아니어도 가젯 페이지 키로는 막는다) */
    public static boolean boardReserved(String key) {
        return key != null && key.startsWith(BOARD_PREFIX);
    }

    /** 게시판 곳 키 */
    public static String boardPlace(long boardId) {
        return BOARD_PREFIX + boardId;
    }

    /** 게시판 곳 키 → 게시판 ID. 게시판 곳 모양({@value #BOARD_PREFIX} + 0 으로 시작하지 않는 양의 정수)이 아니면 빈 값 */
    public static Optional<Long> boardIdOf(String key) {
        if (!wellFormed(key) || !boardReserved(key)) {
            return Optional.empty();
        }
        String digits = key.substring(BOARD_PREFIX.length());
        if (digits.isEmpty() || digits.length() > 19 || digits.charAt(0) == '0' || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(digits));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 모양이 맞는 키인가 */
    public static boolean wellFormed(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    /**
     * 같은 곳을 가리키는 키를 하나로 — 시스템 페이지 TIPS({@code /tips} 의 배치, 키 {@value GadgetPageEntity#TIPS_PAGE_KEY} — 바꿀 수 없다)는 {@value #TIPS} 곳이다
     * (tips-contract 「/tips 레이아웃 편집」 — 하이라이트 곳 목록에 따로 두지 않는다). 그 밖은 그대로.
     */
    public static String canonical(String key) {
        return GadgetPageEntity.TIPS_PAGE_KEY.equals(key) ? TIPS : key;
    }

    /**
     * 요청·설정 값 → 곳 키. 비었으면 {@value #HOME}, 앞뒤 공백은 뗀다. 모양이 틀리면 빈 값(부르는 쪽이 오류 또는 {@value #HOME} 으로 정한다).
     * 시스템 페이지 TIPS 의 키는 {@value #TIPS} 로 바꾼다({@link #canonical}).
     */
    public static Optional<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(HOME);
        }
        String key = raw.strip();
        return wellFormed(key) ? Optional.of(canonical(key)) : Optional.empty();
    }

    /** 한 글의 여러 하이라이트 중 대표 하나 — 홈 것이 먼저, 없으면 먼저 만든 것 */
    public static Optional<HighlightEntity> primary(Collection<HighlightEntity> rows) {
        return rows.stream().min(Comparator.comparing((HighlightEntity h) -> !HOME.equals(h.getPlace())).thenComparing(HighlightEntity::getId));
    }
}
