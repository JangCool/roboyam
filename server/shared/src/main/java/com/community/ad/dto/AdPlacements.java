package com.community.ad.dto;

import java.util.List;

/**
 * 광고 노출 자리 설정(V118 {@code ad_placement_settings}, docs/features/ad-contract.md 「공개 노출 목업」 확정 2026-11-01) — 공개 {@code GET /api/ads/placements} 와
 * 관리자 {@code GET·PUT /api/admin/ads/placements} 가 같은 모양을 쓴다. 저장한 적 없으면 {@link #DEFAULTS}.
 *
 * @param postInline 글 보기 중간 광고 — 켜기 · 유형(한 줄 · 띠 · 네모) · 위치(글 카드와 댓글 카드 사이 · 본문 바로 아래)
 * @param wings      날개 배너 — 왼쪽 · 오른쪽 · 보일 화면(홈 · 가젯 페이지 · 게시판 목록 · 글 보기)
 * @param boardList  게시판 목록 한 줄 광고 — 켜기 · 공지·고정을 뺀 일반 글 몇 번째 뒤(1~20)
 */
public record AdPlacements(PostInline postInline, Wings wings, BoardList boardList) {

    /** 글 보기 중간 광고 유형(상품 이름) */
    public static final List<String> INLINE_TYPES = List.of("TEXT_LINE", "STRIP_BANNER", "SQUARE_BANNER");
    public static final List<String> INLINE_POSITIONS = List.of("BETWEEN_CARDS", "AFTER_BODY");
    /** 날개 배너를 보일 화면(보일 차례) */
    public static final List<String> WING_SCREENS = List.of("HOME", "PAGE", "BOARD", "POST");
    public static final int AFTER_MIN = 1;
    public static final int AFTER_MAX = 20;

    /** 저장한 적 없을 때 — 글 보기 켬 · 한 줄 광고 · 카드 사이, 날개 양쪽 켬 · 네 화면, 목록 한 줄 켬 · 5번째 뒤 */
    public static final AdPlacements DEFAULTS = new AdPlacements(new PostInline(true, "TEXT_LINE", "BETWEEN_CARDS"), new Wings(true, true, WING_SCREENS),
            new BoardList(true, 5));

    public record PostInline(boolean enabled, String adType, String position) {
    }

    public record Wings(boolean left, boolean right, List<String> screens) {

        public Wings {
            screens = screens == null ? List.of() : List.copyOf(screens);
        }
    }

    public record BoardList(boolean enabled, int after) {
    }
}
