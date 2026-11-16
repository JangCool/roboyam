package com.community.board.dto;

import com.community.board.entity.BoardGadget;
import com.community.board.service.BoardFields;
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
 * @param writeTemplate      글 양식(새 글 본문에 미리 채우는 평문 — V153), 없으면 null
 * @param prefixes           이 게시판의 말머리 전부(꺼진 것 포함, 정렬 순서대로)
 * @param gadgetExclusions   이 게시판 글을 내지 않을 가젯(게시판 값 그대로 — 기본 설정에서 물려받지 않는다)
 * @param adsHidden          「이 게시판에 광고 숨김」(게시판 값 그대로) — 켜면 게시판 맥락 광고(목록 한 줄·글 보기·핫딜 노출 · 날개)가 없다. 소모임 게시판은 이 값과 상관없이 없고, 19금 게시판은 광고 자리 설정 {@code adult.enabled} 가 꺼져 있으면 없다
 * @param secretMode         비밀글 방식 OFF · OPTIONAL · ALWAYS(게시판 값 그대로 — V166). 일반·소모임 게시판이 아니면 늘 OFF(V168)
 * @param statusEnabled      처리 상태를 쓰는가(게시판 값 그대로 — V166). 일반·소모임 게시판이 아니면 늘 false(V168)
 * @param chipPosition       글 목록 말머리·상태 칩 위치 ABOVE · BEFORE · BELOW(게시판 값 ?? 기본값 {@code listChipPosition} — V169, 푼 실제 값)
 * @param prefixStyle        글 목록 말머리 모양 TEXT · MUTED · COLOR(게시판 값 ?? 기본값 {@code listPrefixStyle} — V195, 푼 실제 값)
 * @param authorMode         작성자 표시 NICKNAME · ANONYMOUS(게시판 값 그대로 — V184). 일반 게시판이 아니면 늘 NICKNAME(anonymous-board-contract 정책 1)
 * @param authorThumbnailEnabled 글쓴이(회원)가 글쓰기·고치기에서 대표 이미지(목록 썸네일)를 고를 수 있는가(게시판 값 ?? 기본값, 기본 false — V206, list-thumbnail-contract 「2단계」).
 *                           나중에 꺼도 이미 고른 글은 그대로. 운영자 교체는 이 값과 무관
 * @param tagsEnabled        글에 태그를 달 수 있는가(게시판 값 ?? 기본값, 기본 false — V215, post-tags-contract). 꺼져 있으면 글쓰기·고치기의 {@code tags} 를 무시한다(이미 단 태그는 그대로)
 * @param detailList         글 상세 아래 게시판 목록 OFF · LIST · COMPACT(게시판 값 그대로 — V224, post-detail-board-list-contract)
 * @param detailFeedTabsPc   글 상세 아래 [인기글]·[최신글] 탭을 PC 에서 보이는가(게시판 값 ?? 기본값, 기본 false — V226, post-detail-board-list-contract 「2차」)
 * @param detailFeedTabsMobile 같은 탭을 모바일에서 보이는가(게시판 값 ?? 기본값, 기본 true — V226)
 * @param prefixRequired     말머리 필수(게시판 값 ?? 기본값, 기본 false — V226, ai-news-reporter-contract). 켜진 말머리가 하나도 없으면 늘 false(쓸 수 없게 막지 않는다)
 * @param tagRequired        태그 필수(게시판 값 ?? 기본값, 기본 false — V226). 「태그 쓰기」({@code tagsEnabled})가 꺼져 있으면 늘 false
 * @param sourcesEnabled     글에 출처 목록({@code posts.sources})을 받는가(게시판 값 ?? 기본값, 기본 false — V232). 꺼져 있으면 글쓰기·고치기의 {@code sources} 를 무시하고 빈 값으로 저장한다
 * @param adultOnly          성인 전용 게시판(게시판 값 그대로 — V226, adult-board-contract). 일반 게시판만(그 밖 늘 false). 켜져 있으면 {@code allowAdult} 도 true 이고
 *                           그 게시판 글은 모두 19금으로 저장한다. 성인이 아니면 게시판·글·메뉴에서 없는 게시판과 같다
 * @param indexable          검색엔진 색인(게시판 값 ?? 기본값, 기본 켬) — 끄면 공개 게시판 응답·글 응답의 {@code indexable} 이 false 이고 화면이 robots noindex 를 단다
 * @param searchable         통합검색 포함(게시판 값 ?? 기본값, 기본 켬) — 끄면 「전체 게시판」 통합검색에서 뺀다(그 게시판만 고른 검색은 그대로)
 * @param inHome             홈·인기·최신 포함(게시판 값 ?? 기본값, 기본 켬) — 끄면 {@link #excludedFrom} 이 모든 가젯에 참(최신글·인기글 모음과 「전체 게시판」 가젯에서 빠진다)
 *                           세 칸은 관리자 화면에만 있고 공개 쪽이 읽지 않았다(관리자 QA 2026-11-17 F-7)
 */
public record EffectiveBoard(long id, String slug, String name, String nameEn, String description, String kind, String layout, boolean viewToggle,
                             String color, boolean allowAdult, boolean investmentNotice, boolean active, int sortOrder, String writeLock, String introText,
                             String writeGuideText, String writeTemplate, String writeMin, String commentMin, String postEditor, boolean imageUploadAllowed, boolean imageSetting,
                             String commentEditor,
                             int listSize, int pinnedMax,
                             int commentProtectMin, boolean commentProtectEdit, String commentProtectDelete, int commentProtectDelayMinutes,
                             int commentGuestDelayMinutes, boolean staffHiddenNotice, boolean commentDislikeEnabled, String commentLikeLabel,
                             String commentDislikeLabel, List<String> pinRequestReviewers, boolean debateEnabled, boolean pollEnabled, boolean pointsEnabled,
                             boolean pointMinLengthApplied, List<BoardPrefixInfo> prefixes, Set<BoardGadget> gadgetExclusions,
                             boolean adsHidden, String secretMode, boolean statusEnabled, String chipPosition, String authorMode,
                             String prefixStyle, boolean authorThumbnailEnabled, boolean tagsEnabled, String detailList, boolean detailFeedTabsPc,
                             boolean detailFeedTabsMobile, boolean prefixRequired, boolean tagRequired, boolean adultOnly,
                             boolean sourcesEnabled, boolean indexable, boolean searchable, boolean inHome) {

    public EffectiveBoard {
        secretMode = secretMode == null ? BoardFields.SECRET_OFF : secretMode;
        chipPosition = chipPosition == null ? BoardFields.CHIP_ABOVE : chipPosition;
        authorMode = authorMode == null ? BoardFields.AUTHOR_NICKNAME : authorMode;
        prefixStyle = prefixStyle == null ? BoardFields.PREFIX_STYLE_TEXT : prefixStyle;
        detailList = detailList == null ? BoardFields.DETAIL_LIST_LIST : detailList;
        pinRequestReviewers = pinRequestReviewers == null ? List.of() : List.copyOf(pinRequestReviewers);
        gadgetExclusions = gadgetExclusions == null || gadgetExclusions.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(gadgetExclusions));
    }

    /** 이 가젯에 이 게시판 글을 내지 않는가(게시판 설정 「가젯 노출 금지」, 또는 「홈·인기·최신 포함」을 껐다) */
    public boolean excludedFrom(BoardGadget gadget) {
        return !inHome || gadgetExclusions.contains(gadget);
    }

    /** 익명 게시판인가(V184) — 새 글·댓글을 익명으로 쓰고, 회원만 쓴다(비회원 쓰기 401 — anonymous-board-contract 정책 1·2) */
    public boolean anonymous() {
        return BoardFields.AUTHOR_ANONYMOUS.equals(authorMode);
    }

    /** 모든 회원 글이 비밀글인가(ALWAYS) */
    public boolean secretAlways() {
        return BoardFields.SECRET_ALWAYS.equals(secretMode);
    }

    /** 글쓴이가 비밀글 여부를 고르는가(OPTIONAL) */
    public boolean secretOptional() {
        return BoardFields.SECRET_OPTIONAL.equals(secretMode);
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
