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
 * @param sealedSource    「이 글/댓글 작성자」로 건 제재(V184 — anonymous-board-contract 정책 9)면 그 글·댓글, 아니면 null. 봉인 제재는 영구 정지 닉네임 확인을 받지 않고
 *                        (운영자는 닉네임을 볼 수 없다), 감사 대상이 회원이 아니라 그 글·댓글이다
 */
public record SanctionIssue(long userId, String kind, String reason, boolean skipStep, String confirmNickname, Long reportCaseId, String fieldPrefix,
                            SealedSource sealedSource) {

    /** 봉인 제재의 계기 — 댓글이면 {@code commentId} 가 있다 */
    public record SealedSource(long postId, Long commentId) {
    }

    /** 회원을 고른 제재(봉인 아님) */
    public SanctionIssue(long userId, String kind, String reason, boolean skipStep, String confirmNickname, Long reportCaseId, String fieldPrefix) {
        this(userId, kind, reason, skipStep, confirmNickname, reportCaseId, fieldPrefix, null);
    }

    public boolean sealed() {
        return sealedSource != null;
    }

    @Override
    public String toString() {
        return "SanctionIssue[userId=" + userId + ", kind=" + kind + ", skipStep=" + skipStep + ", reportCaseId=" + reportCaseId + "]";
    }
}
