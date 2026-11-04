package com.community.sanction.dto;

/**
 * 제재 부여 요청(관리자 [제재하기] · 신고 처리에 곁들인 제재) — 검사는 서비스가 한다.
 *
 * @param kind            단계 이름(모르면 400 {@code fields.<prefix>kind})
 * @param reason          회원에게 그대로 가는 사유(1~500자)
 * @param skipStep        다음 단계보다 높은 제재를 의도했다
 * @param confirmNickname 영구 정지면 회원 닉네임 그대로
 * @param reportCaseId    근거 신고 사건(없으면 null — 있는지는 부르는 쪽이 확인했다)
 * @param fieldPrefix     칸 오류 경로 앞머리 — [제재하기] 는 빈 글자, 신고 처리는 {@code sanction.}
 */
public record SanctionIssue(long userId, String kind, String reason, boolean skipStep, String confirmNickname, Long reportCaseId, String fieldPrefix) {

    @Override
    public String toString() {
        return "SanctionIssue[userId=" + userId + ", kind=" + kind + ", skipStep=" + skipStep + ", reportCaseId=" + reportCaseId + "]";
    }
}
