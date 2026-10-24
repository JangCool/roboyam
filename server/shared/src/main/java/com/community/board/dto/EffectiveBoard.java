package com.community.board.dto;

import com.community.board.entity.BoardGadget;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 게시판 하나와 그 <b>유효 정책</b>(게시판 값 ?? 기본값)의 읽기 사본 — {@link com.community.board.service.BoardCatalog} 가 만든다.
 * 게시판별로 덮어쓸 수 없는 칸(작성 간격·하루 한도·베스트 댓글 기준 등)은 {@link BoardCatalogSnapshot#defaults()} 를 본다.
 *
 * @param postEditor         글 에디터 유효값 NONE(평문) · SIMPLE · ADVANCED
 * @param imageUploadAllowed 에디터 이미지 올리기 유효값 — 이미지는 고급 에디터만 받으므로 글 에디터가 ADVANCED 가 아니면 늘 false
 * @param imageSetting       이미지 허용 칸의 값 그대로(게시판 값 ?? 기본값, 에디터와 무관) — 평문 본문의 이미지 주소 규칙(예전과 같다)이 본다
 * @param commentEditor      댓글 에디터 유효값 NONE · SIMPLE
 * @param prefixes           이 게시판의 말머리 전부(꺼진 것 포함, 정렬 순서대로)
 * @param gadgetExclusions   이 게시판 글을 내지 않을 홈 가젯(게시판 값 그대로 — 기본 설정에서 물려받지 않는다)
 */
public record EffectiveBoard(long id, String slug, String name, String nameEn, String description, String kind, String layout, boolean viewToggle,
                             String color, boolean allowAdult, boolean investmentNotice, boolean active, int sortOrder, String writeLock, String introText,
                             String writeGuideText, String writeMin, String commentMin, String postEditor, boolean imageUploadAllowed, boolean imageSetting,
                             String commentEditor,
                             int listSize, int pinnedMax,
                             int commentProtectMin, boolean commentProtectEdit, String commentProtectDelete, int commentProtectDelayMinutes,
                             int commentGuestDelayMinutes, List<BoardPrefixInfo> prefixes, Set<BoardGadget> gadgetExclusions) {

    public EffectiveBoard {
        gadgetExclusions = gadgetExclusions == null || gadgetExclusions.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(gadgetExclusions));
    }

    /** 이 가젯에 이 게시판 글을 내지 않는가(게시판 설정 「가젯 노출 금지」) */
    public boolean excludedFrom(BoardGadget gadget) {
        return gadgetExclusions.contains(gadget);
    }

    /** 글 본문으로 HTML 을 받는가(간단·고급 에디터) */
    public boolean postHtml() {
        return !"NONE".equals(postEditor);
    }

    /** 댓글 본문으로 HTML 을 받는가(간단 에디터) */
    public boolean commentHtml() {
        return !"NONE".equals(commentEditor);
    }

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
