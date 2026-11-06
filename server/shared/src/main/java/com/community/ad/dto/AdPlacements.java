package com.community.ad.dto;

import java.util.List;

/**
 * 광고 노출 자리 설정(V118 {@code ad_placement_settings}, docs/features/ad-contract.md 「공개 노출 목업」 확정 2026-11-01) — 공개 {@code GET /api/ads/placements} 와
 * 관리자 {@code GET·PUT /api/admin/ads/placements} 가 같은 모양을 쓴다. 저장한 적 없으면 {@link #DEFAULTS}.
 *
 * @param postInline 글 보기 중간 광고 — 켜기 · 유형(한 줄 · 띠 · 네모) · 위치(글 카드와 댓글 카드 사이 · 본문 바로 아래)
 * @param wings      날개 배너 — 왼쪽 · 오른쪽 · 보일 화면(홈 · 가젯 페이지 · 게시판 목록 · 글 보기)
 * @param boardList  게시판 목록 한 줄 광고 — 켜기 · 공지·고정을 뺀 일반 글 몇 번째 뒤(1~20) · 한 쪽 개수(1~2) · 두 광고 사이 글 수(3~20) — 개수·간격 V155
 * @param adult      19금 게시판에도 광고 보이기(V119, 사용자 결정 2026-11-01 2차) — 끄면 19금 게시판의 게시판 맥락 광고(날개 포함)는 빈 목록. 숨김·소모임 게시판은 이 칸과
 *                   상관없이 늘 제외
 */
public record AdPlacements(PostInline postInline, Wings wings, BoardList boardList, Adult adult) {

    /** 글 보기 중간 광고 유형(상품 이름) */
    public static final List<String> INLINE_TYPES = List.of("TEXT_LINE", "STRIP_BANNER", "SQUARE_BANNER");
    public static final List<String> INLINE_POSITIONS = List.of("BETWEEN_CARDS", "AFTER_BODY");
    /** 날개 배너를 보일 화면(보일 차례) — FEED 최신글·인기글 · GROUP_BUY 공동구매 · GROUPS 소모임(2026-11-06 넓힘, V155) */
    public static final List<String> WING_SCREENS = List.of("HOME", "PAGE", "BOARD", "POST", "FEED", "GROUP_BUY", "GROUPS");
    public static final int AFTER_MIN = 1;
    public static final int AFTER_MAX = 20;
    /** 목록 한 쪽 한 줄 광고 개수(V155 — 사용자 2026-11-06 「2개까지」) */
    public static final int COUNT_MIN = 1;
    public static final int COUNT_MAX = 2;
    /** 두 광고 사이 일반 글 수(V155) */
    public static final int GAP_MIN = 3;
    public static final int GAP_MAX = 20;
    public static final int COUNT_DEFAULT = 1;
    public static final int GAP_DEFAULT = 10;

    /** 저장한 적 없을 때 — 글 보기 켬 · 한 줄 광고 · 카드 사이, 날개 양쪽 켬 · 모든 화면, 목록 한 줄 켬 · 5번째 뒤 · 1개(간격 10), 19금 게시판 끔 */
    public static final AdPlacements DEFAULTS = new AdPlacements(new PostInline(true, "TEXT_LINE", "BETWEEN_CARDS"), new Wings(true, true, WING_SCREENS),
            new BoardList(true, 5, COUNT_DEFAULT, GAP_DEFAULT), new Adult(false));

    public record PostInline(boolean enabled, String adType, String position) {
    }

    public record Wings(boolean left, boolean right, List<String> screens) {

        public Wings {
            screens = screens == null ? List.of() : List.copyOf(screens);
        }
    }

    /** @param count 한 쪽 개수 1~2 · @param gap 첫 광고와 둘째 광고 사이 일반 글 수 */
    public record BoardList(boolean enabled, int after, int count, int gap) {
    }

    public record Adult(boolean enabled) {
    }
}
