package com.community.hotdeal.dto.response;

/** 핫딜 게시판 응답의 공개 설정 칸({@code BoardDetail.dealRules}, 메모 1-1) — 화면의 미리 검사용(최종 판정은 서버) */
public record DealRulesView(long priceMaxWon, int discountWarnPercent, int autoExpireDays, int hotLikeThreshold) {
}