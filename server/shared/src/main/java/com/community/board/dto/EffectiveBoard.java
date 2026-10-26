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
 * @param staffHiddenNotice  운영자가 숨긴 댓글임을 공개 화면에 밝히는가(게시판 값 ?? 기본값, 기본 false) — 끄면 운영자 숨김도 신고 숨김과 구별되지 않게 까닭을 내리지 않는다
 * @param commentDislikeEnabled 댓글 비추천을 받는가(게시판 값 ?? 기본값, 기본 false) — 끄면 추천만, 쌓인 비추천 수는 응답에 싣지 않는다
 * @param commentLikeLabel   댓글 추천 단추 이름(게시판 값 ?? 기본값, 둘 다 없으면 null = 화면 기본 이름)
 * @param commentDislikeLabel 댓글 비추천 단추 이름(같은 규칙)
 * @param pinRequestReviewers 의견 고정 요청을 확인하는 사람(게시판 값 ?? 기본값) — POST_AUTHOR · BOARD_MANAGER · OPERATOR, 비면 고정 요청 끔
 * @param debateEnabled      글쓴이가 자기 글에 찬반 토론을 열 수 있는가(게시판 값 ?? 기본값, 기본 false — docs/features/post-debate-contract.md). 나중에 꺼도 이미 연 토론은 그대로
 * @param pollEnabled        글쓴이(회원)가 자기 글에 투표를 붙일 수 있는가(게시판 값 ?? 기본값, 기본 false — docs/features/post-poll-contract.md). 나중에 꺼도 이미 붙은 투표는 그대로
 * @param pointsEnabled      이 게시판 글·댓글·추천 보너스에 포인트를 주는가(유효 {@code pointMode} = ON — 커머스 1-3 확정 2)
 * @param pointMinLengthApplied 「적립 최소 글자 수 적용」(게시판 값 ?? 기본값, 기본 켬) — 끄면 글 본문 최소 글자 수만 빠진다(커머스 1-3 확정 1)
 * @param prefixes           이 게시판의 말머리 전부(꺼진 것 포함, 정렬 순서대로)
 * @param gadgetExclusions   이 게시판 글을 내지 않을 홈 가젯(게시판 값 그대로 — 기본 설정에서 물려받지 않는다)
 */
public record EffectiveBoard(long id, String slug, String name, String nameEn, String description, String kind, String layout, boolean viewToggle,
                             String color, boolean allowAdult, boolean investmentNotice, boolean active, int sortOrder, String writeLock, String introText,
                             String writeGuideText, String writeMin, String commentMin, String postEditor, boolean imageUploadAllowed, boolean imageSetting,
                             String commentEditor,
                             int listSize, int pinnedMax,
                             int commentProtectMin, boolean commentProtectEdit, String commentProtectDelete, int commentProtectDelayMinutes,
                             int commentGuestDelayMinutes, boolean staffHiddenNotice, boolean commentDislikeEnabled, String commentLikeLabel,
                             String commentDislikeLabel, List<String> pinRequestReviewers, boolean debateEnabled, boolean pollEnabled, boolean pointsEnabled,
                             boolean pointMinLengthApplied, List<BoardPrefixInfo> prefixes, Set<BoardGadget> gadgetExclusions) {

    public EffectiveBoard {
        pinRequestReviewers = pinRequestReviewers == null ? List.of() : List.copyOf(pinRequestReviewers);
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
