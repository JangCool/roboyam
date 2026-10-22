package com.community.board.dto;

import java.util.List;
import java.util.Optional;

/**
 * 게시판 하나와 그 <b>유효 정책</b>(게시판 값 ?? 기본값)의 읽기 사본 — {@link com.community.board.service.BoardCatalog} 가 만든다.
 * 게시판별로 덮어쓸 수 없는 칸(작성 간격·하루 한도·베스트 댓글 기준 등)은 {@link BoardCatalogSnapshot#defaults()} 를 본다.
 *
 * @param prefixes 이 게시판의 말머리 전부(꺼진 것 포함, 정렬 순서대로)
 */
public record EffectiveBoard(long id, String slug, String name, String nameEn, String description, String kind, String layout, boolean viewToggle,
                             String color, boolean allowAdult, boolean investmentNotice, boolean active, int sortOrder, String writeLock, String introText,
                             String writeGuideText, String writeMin, String commentMin, boolean imageUploadAllowed, int listSize, int pinnedMax,
                             int commentProtectMin, boolean commentProtectEdit, String commentProtectDelete, int commentProtectDelayMinutes,
                             int commentGuestDelayMinutes, List<BoardPrefixInfo> prefixes) {

    /** 켜진 말머리만(고르기 목록) */
    public List<BoardPrefixInfo> activePrefixes() {
        return prefixes.stream().filter(BoardPrefixInfo::active).toList();
    }

    /** 이 게시판의 말머리(꺼진 것 포함) */
    public Optional<BoardPrefixInfo> prefix(Long prefixId) {
        if (prefixId == null) {
            return Optional.empty();
        }
        return prefixes.stream().filter(p -> p.id() == prefixId).findFirst();
    }

    /** 새 글을 막는 잠금인가(POST · POST_COMMENT) */
    public boolean postLocked() {
        return writeLock != null;
    }

    /** 새 댓글까지 막는 잠금인가 */
    public boolean commentLocked() {
        return "POST_COMMENT".equals(writeLock);
    }
}
