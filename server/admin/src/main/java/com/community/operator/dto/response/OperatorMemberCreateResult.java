package com.community.operator.dto.response;

import java.util.List;

/**
 * {@code POST /api/admin/operators/{operatorId}/member-account} 응답(201, admin-web {@code OperatorMemberCreateResult}) — 만든 회원과 이은 뒤의 사이트 계정 상태.
 *
 * @param userId       만든 회원 ID(= 회원 관리의 회원번호)
 * @param email        정규화한(소문자) 이메일 — 이 주소로 메일을 보낸다
 * @param passwordMode {@code DIRECT} · {@code MAIL}
 * @param mailsSent    대기열에 넣은 메일 — {@code EMAIL_VERIFICATION}(DIRECT) · {@code PASSWORD_SETUP}(MAIL). 커밋 뒤 대기열 워커가 보낸다
 * @param account      이은 뒤 상태({@code GET …/member-account} 와 같은 모양 — {@code linked} 가 채워져 온다)
 */
public record OperatorMemberCreateResult(String userId, String email, String nickname, String passwordMode, List<String> mailsSent, OperatorMemberAccountView account) {

    /** 이메일을 싣지 않는다 */
    @Override
    public String toString() {
        return "OperatorMemberCreateResult[userId=" + userId + ", email=(감춤), passwordMode=" + passwordMode + ", mailsSent=" + mailsSent + "]";
    }
}
