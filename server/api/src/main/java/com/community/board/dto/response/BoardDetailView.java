package com.community.board.dto.response;

import com.community.hotdeal.dto.response.DealRulesView;

import java.util.List;

/**
 * {@code GET /api/boards/{slug}} — 게시판 요약 + 유효 정책 + 지금 보는 사람의 판정(user-web {@code BoardDetail}).
 *
 * @param writeMinGradeLabel   {@code writeMin} 이 등급 키일 때 그 등급의 이름(안내 문구용), 아니면 null
 * @param commentMinGradeLabel {@code commentMin} 이 등급 키일 때 그 등급의 이름, 아니면 null
 * @param introText            게시판 안내문(평문), 없으면 null
 * @param writeGuideText       글쓰기 안내문(평문), 없으면 null
 * @param reportEnabled        신고 기능 사용(관리자 신고 정책)
 * @param debateEnabled        글쓴이가 자기 글에 찬반 토론을 열 수 있는가(유효 정책, {@link BoardView#debateEnabled()} 와 같다)
 * @param pollEnabled          글쓴이가 자기 글에 투표를 붙일 수 있는가(유효 정책, {@link BoardView#pollEnabled()} 와 같다)
 * @param kind                 게시판 종류 GENERAL · DEAL · PROMO · GROUP
 * @param dealAffiliateMode    핫딜 게시판이면 지금 제휴 링크 방식(MEMBER · SITE — D5), 아니면 null
 * @param dealRules            핫딜 게시판이면 핫딜 설정 공개 칸(금액 상한·할인율 경고·기간 종료 일수·HOT 문턱 — 메모 1-1), 아니면 null
 * @param adsHidden            관리자 「이 게시판에 광고 숨김」 — 켜면 목록 한 줄·글 보기·핫딜 노출 광고가 없다(19금 게시판은 광고 자리 설정 {@code adult.enabled} 가 꺼져 있으면 없다 — 화면은 {@code adsHidden || (allowAdult && !adult.enabled)})
 */
public record BoardDetailView(String id, String slug, String name, String nameEn, String description, boolean investmentNotice, boolean staffOnlyWrite,
                              String writeMin, String commentMin, String writeLock, boolean imageUploadAllowed, String postEditor, String commentEditor,
                              String layout, boolean dealEnabled,
                              boolean promo, boolean viewToggle, boolean allowAdult, List<PrefixView> prefixes, String color, String writeMinGradeLabel,
                              String commentMinGradeLabel, String introText, String writeGuideText, boolean reportEnabled, BoardPolicyView policy,
                              BoardGuestRulesView guestRules, BoardNewMemberRulesView newMemberRules, BoardViewerView viewer,
                              boolean debateEnabled, boolean pollEnabled, boolean adsHidden, String kind, String dealAffiliateMode, DealRulesView dealRules) {
}
