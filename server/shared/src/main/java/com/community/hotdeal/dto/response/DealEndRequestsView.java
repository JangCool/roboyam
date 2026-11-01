package com.community.hotdeal.dto.response;

/** 종료 요청 집계 — 요청자 신원은 싣지 않는다. {@code mine} = 내가 보낸 사유(안 보냈거나 비회원이면 null) */
public record DealEndRequestsView(int total, int bySoldOut, int byPriceChanged, int byLinkDead, String mine) {

    public static final DealEndRequestsView NONE = new DealEndRequestsView(0, 0, 0, 0, null);
}