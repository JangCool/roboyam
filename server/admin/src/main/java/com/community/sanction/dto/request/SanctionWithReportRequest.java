package com.community.sanction.dto.request;

/** 신고 처리({@code resolve})에 곁들인 작성자 제재 — 대상은 사건의 작성자(서버가 정한다), {@code reason} 이 null 이면 처리 글과 같다 */
public record SanctionWithReportRequest(String kind, Boolean skipStep, String confirmNickname, String reason) {
}
