package com.community.member.dto.response;

/**
 * 신고 요약 — admin-web {@code MemberReportSummary}. 신고 표({@code reports})에 남아 있는 것만 센다 — 개인정보 보유기간 배치(신고 기록 축, {@code R__batch_18})가
 * 오래된 신고를 지우면 수가 줄어든다.
 *
 * @param receivedCount 이 회원이 대상 작성자인 신고 수(이 회원의 글·댓글이 받은 신고, {@code reports.target_author_id})
 * @param actionedCount 그중 조치된(수용 {@code ACCEPTED}) 수 — 신고 처리 기능이 아직 없어 지금은 0
 * @param filedCount    이 회원이 한 신고 수({@code reports.reporter_id})
 */
public record MemberReportSummary(long receivedCount, long actionedCount, long filedCount) {
}
