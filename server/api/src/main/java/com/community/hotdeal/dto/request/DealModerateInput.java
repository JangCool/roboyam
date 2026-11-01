package com.community.hotdeal.dto.request;

/**
 * 게시판 매니저·스텝의 종료·재개({@code POST /api/posts/{id}/deal/moderate/end}·{@code /reopen}) — 사유는 앞뒤 공백을 떼고 종료 1~200자(필수 — 작성자 알림에 실린다)·재개 0~200자
 */
public record DealModerateInput(String reason) {
}
