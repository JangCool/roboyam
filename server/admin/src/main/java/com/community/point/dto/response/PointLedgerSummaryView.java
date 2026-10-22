package com.community.point.dto.response;

/**
 * 같은 조건으로 거른 줄 전체(쪽이 아니라)의 합계 — admin-web {@code PointLedgerSummary}.
 *
 * @param earned      지급 합계(+ 줄의 합)
 * @param spent       차감·회수·사용 합계(− 줄의 크기의 합)
 * @param adjustCount 운영 조정({@code ADMIN_ADJUST}) 건수
 */
public record PointLedgerSummaryView(long earned, long spent, long adjustCount) {
}
