package com.community.operator.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/admin/operators/{operatorId}/member-account/candidates?q=}(operator-member-account-contract 「세션 정리 결정 2026-11-24」) — 관리자가 회원을 검색해 고르는 후보, 최대 20건.
 * 탈퇴를 마친 회원과 이 운영자에 이미 이어진 회원은 오지 않는다. 정확히 맞는 것 먼저, 그다음 가입 최신순.
 */
public record OperatorMemberCandidates(List<Item> items) {

    /**
     * 후보 한 줄 — {@link OperatorMemberAccountView.Candidate} 와 같은 칸 + {@code memberNo}·{@code maskedEmail}.
     *
     * @param memberNo           회원번호(관리자 회원 관리의 회원번호 = 회원 ID)
     * @param maskedEmail        가린 이메일({@code ab***@gmail.com} — 관리자 회원 목록과 같은 가림), 이메일이 없으면 null
     * @param ineligibleReasons  EMAIL_NOT_VERIFIED · MEMBER_RESTRICTED · BUSINESS_ACCOUNT · LINKED_TO_OTHER(여럿 가능)
     * @param linkedOperatorName {@code LINKED_TO_OTHER} 일 때 그 운영자 이름 — 아니면 null
     */
    public record Item(String userId, String memberNo, String nickname, String maskedEmail, String grade, Instant joinedAt, int boardManagerCount,
                       int boardStaffCount, boolean eligible, List<String> ineligibleReasons, String linkedOperatorName) {
    }
}
