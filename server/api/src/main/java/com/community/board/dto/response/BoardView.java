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
 * @param debateEnabled  글쓴이가 자기 글에 찬반 토론을 열 수 있는가(유효 정책 {@code debateEnabled}, 기본 false) — 글쓰기·고치기의 「찬반 토론」 칸(회원일 때)
 * @param pollEnabled    글쓴이가 자기 글에 투표를 붙일 수 있는가(유효 정책 {@code pollEnabled}, 기본 false) — 글쓰기·고치기의 「투표」 칸(회원일 때)
 * @param kind           게시판 종류 GENERAL · DEAL · PROMO(공개 API 에 소모임 게시판은 나오지 않는다) — 핫딜 계약(hotdeal-contract)
 * @param secretMode     비밀글 방식 OFF · OPTIONAL(글쓴이가 고름) · ALWAYS(모든 회원 글 비밀) — V166 운영 게시판 설정(일반·소모임 게시판만 — V168, 그 밖 OFF)
 * @param statusEnabled  처리 상태를 쓰는가(V166 — 일반·소모임 게시판만(V168), 그 밖 false)
 * @param chipPosition   글 목록 말머리·상태 칩 위치 ABOVE(제목 위) · BEFORE(제목 앞) · BELOW(닉네임 아래) — 게시판 값 또는 사이트 기본을 푼 실제 값(V169, post-chip-position-contract)
 * @param authorMode     작성자 표시 NICKNAME · ANONYMOUS(V184 — 익명 게시판은 회원만 쓰고 글·댓글 작성자가 「익명」, anonymous-board-contract). 일반 게시판만 ANONYMOUS 일 수 있다
 * @param prefixStyle    글 목록·글 보기 말머리 모양 TEXT(글자) · MUTED(회색 칩) · COLOR(색상 칩) — 게시판 값 또는 사이트 기본을 푼 실제 값(V195, mobile-nav-prefix-style-contract 1절)
 * @param detailList     글 상세 아래 게시판 목록 OFF(안 보임) · LIST(게시판 목록 모양, 기본) · COMPACT(휴대폰은 촘촘한 한 줄) — 게시판 값(V224, post-detail-board-list-contract)
 * @param detailFeedTabsPc     글 상세 아래 [인기글]·[최신글] 탭을 PC 에서 보이는가 — 유효값(게시판 값 ?? 사이트 기본, 기본 false — V226, post-detail-board-list-contract 「2차」)
 * @param detailFeedTabsMobile 같은 탭을 모바일에서 보이는가 — 유효값(기본 true — V226)
 * @param prefixRequired 말머리 필수 — 유효값(V226, ai-news-reporter-contract). 켜져 있으면 말머리 없이 쓰기·고치기 400 {@code fields.prefix}(켜진 말머리가 없는 게시판은 늘 false)
 * @param tagRequired    태그 필수 — 유효값(V226). 켜져 있으면 태그 0개로 쓰기·고치기 400 {@code fields.tags}(「태그 쓰기」가 꺼진 게시판은 늘 false)
 * @param adultOnly      성인 전용 게시판(V226, adult-board-contract) — 성인에게만 응답에 나온다(아니면 목록에서 빠지고 게시판·글은 404). 켜져 있으면 {@code allowAdult} 도 true, 글은 늘 19금
 */
public record BoardView(String id, String slug, String name, String nameEn, String description, boolean investmentNotice, boolean staffOnlyWrite,
                        String writeMin, String commentMin, String writeLock, boolean imageUploadAllowed, String postEditor, String commentEditor, String layout,
                        boolean dealEnabled, boolean promo,
                        boolean viewToggle, boolean allowAdult, List<PrefixView> prefixes, String color, boolean debateEnabled, boolean pollEnabled, String kind,
                        String secretMode, boolean statusEnabled, String chipPosition, String authorMode, String prefixStyle, String detailList,
                        boolean detailFeedTabsPc, boolean detailFeedTabsMobile, boolean prefixRequired, boolean tagRequired, boolean adultOnly) {
}
