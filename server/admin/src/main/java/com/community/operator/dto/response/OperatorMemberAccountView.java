package com.community.operator.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET|PUT|DELETE /api/admin/operators/{operatorId}/member-account}(admin-web {@code OperatorMemberAccountView}, operator-member-account-contract 「API > 관리자」).
 * {@code linked} 와 {@code candidate} 는 함께 오지 않는다 — 이어져 있으면 {@code linked}, 아니면 {@code candidate}(같은 이메일로 가입한 회원이 없으면 null).
 */
public record OperatorMemberAccountView(Linked linked, Candidate candidate, Powers powers) {

    /**
     * 이어진 회원 계정.
     *
     * @param grade         등급 키
     * @param linkedBy      이은 운영자 — 기록이 없으면(그 운영자가 지워짐) null
     * @param active        지금 힘이 살아 있나 — {@code pausedReasons} 가 비었을 때만 true
     * @param pausedReasons OPERATOR_UNAVAILABLE · MEMBER_SUSPENDED
     */
    public record Linked(String userId, String nickname, String grade, Instant linkedAt, Actor linkedBy, boolean active, List<String> pausedReasons) {
    }

    /** 연결한 운영자 */
    public record Actor(String operatorId, String operatorName) {
    }

    /**
     * 이 운영자 이메일과 같은 이메일로 가입한 회원.
     *
     * @param ineligibleReasons  EMAIL_NOT_VERIFIED · MEMBER_RESTRICTED · BUSINESS_ACCOUNT · LINKED_TO_OTHER(여럿 가능)
     * @param linkedOperatorName {@code LINKED_TO_OTHER} 일 때 그 운영자 이름 — 아니면 null
     */
    public record Candidate(String userId, String nickname, String grade, Instant joinedAt, int boardManagerCount, int boardStaffCount, boolean eligible,
                            List<String> ineligibleReasons, String linkedOperatorName) {
    }

    /**
     * 힘 한 줄.
     *
     * @param key        ROBOTICON_ALL · OPERATOR_BADGE · POST_MODERATE · COMMENT_DELETE · AUTHOR_SANCTION · ACTION_READ · ACTION_RESTORE
     * @param permission 필요한 관리자 권한 코드 — 권한과 상관없는 힘은 null
     * @param granted    이 운영자가 그 권한을 가졌나(최고 관리자는 전부) — 연결 여부·멈춤과 상관없는 값
     */
    public record Power(String key, String permission, boolean granted) {
    }

    /** @param dailyLimit 게시판 처리의 하루 한도 — 운영자 회원 계정은 한도가 없어 늘 null */
    public record Powers(List<Power> items, Integer dailyLimit) {
    }
}
