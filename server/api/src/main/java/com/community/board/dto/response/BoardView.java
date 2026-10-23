package com.community.board.dto.response;

import java.util.List;

/**
 * 게시판 요약 — {@code GET /api/boards}·{@code /api/boards/writable} 의 한 줄(user-web {@code BoardView}). 정책 칸은 유효값(게시판 값 ?? 기본값).
 *
 * @param description    설명, 없으면 null
 * @param staffOnlyWrite 유효 {@code writeMin = STAFF}(매니저·스텝만 글쓰기)
 * @param writeLock      읽기 전용 잠금 POST · POST_COMMENT, 없으면 null
 * @param imageUploadAllowed 에디터 이미지 올리기 유효값 — 글 에디터가 ADVANCED 이고 이미지 허용일 때만 true
 * @param postEditor     글 에디터 유효값 NONE(평문 — HTML 을 받지 않는다) · SIMPLE · ADVANCED
 * @param commentEditor  댓글 에디터 유효값 NONE · SIMPLE
 * @param dealEnabled    핫딜 게시판(kind = DEAL)
 * @param promo          직접홍보 게시판(kind = PROMO)
 * @param prefixes       켜진 말머리, 정렬 순서대로
 */
public record BoardView(String id, String slug, String name, String nameEn, String description, boolean investmentNotice, boolean staffOnlyWrite,
                        String writeMin, String commentMin, String writeLock, boolean imageUploadAllowed, String postEditor, String commentEditor, String layout,
                        boolean dealEnabled, boolean promo,
                        boolean viewToggle, boolean allowAdult, List<PrefixView> prefixes, String color) {
}
