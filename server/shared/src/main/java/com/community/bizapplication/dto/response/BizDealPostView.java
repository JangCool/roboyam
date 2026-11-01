package com.community.bizapplication.dto.response;

import java.time.Instant;

/**
 * 사업자 핫딜 신청 건의 「게시된 글」 칸(user-web·admin-web {@code BizDealPost} — biz-hotdeal-contract 「데이터 모양」). 공개 신청 건 상세·관리자 상세·사업자 종료 응답이 같은 모양.
 *
 * @param status     WAITING(게시 전) · POSTED · FAILED(올리지 못함) · HIDDEN(취소·환불·내림·운영자 숨김 — 글은 지우지 않는다)
 * @param postId     올라간 글(게시 전·실패면 null)
 * @param publishAt  게시(예정) 시각 = 신청 기간 시작
 * @param view       딜 상태 ACTIVE·FLAGGED·ENDED·EXPIRED(게시 전·실패면 null) — 딜 마감 = 신청 기간 끝
 * @param endedBy    ENDED 일 때 AUTHOR(사업자)·MANAGER(운영진)·AUTO(종료 요청 자동)
 * @param failReason BOARD_CLOSED·LINK_BLOCKED·BUSINESS_SUSPENDED·OTHER(실패만)
 * @param placement  같이 산 노출 자리(없으면 null)
 * @param autoEndAt  의심(FLAGGED)이면 자동 종료 예정 시각(유예 0 이면 null)
 * @param endedAt    딜이 끝난 시각(ENDED 만)
 */
public record BizDealPostView(String status, String postId, String boardSlug, String boardName, String prefixName, Instant publishAt, String view, String endedBy,
                              EndRequests endRequests, String failReason, Placement placement, Instant autoEndAt, Instant endedAt) {

    /** 종료 요청 사유별 수(요청자 신원 없음) — 게시 전·실패면 null */
    public record EndRequests(int total, int bySoldOut, int byPriceChanged, int byLinkDead) {
    }

    /**
     * @param product    HOTDEAL_TOP · HOTDEAL_CATEGORY
     * @param status     WAITING(게시 전·실패) · SHOWING · STOPPED(딜 끝남·글 숨김) · ENDED(기간 끝)
     * @param stopReason DEAL_ENDED · POST_HIDDEN(STOPPED 만)
     */
    public record Placement(String product, String status, String stopReason) {
    }
}
