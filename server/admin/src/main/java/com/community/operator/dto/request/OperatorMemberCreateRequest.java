package com.community.operator.dto.request;

/**
 * {@code POST /api/admin/operators/{operatorId}/member-account} 본문 — 새 회원 계정을 만들고 이 운영자에 바로 잇는다(admin-web {@code OperatorMemberCreateRequest}).
 * 칸별 문구가 필요해 서비스가 검사한다({@code fields.email|nickname|password|passwordMode}). 앞뒤를 자르지 않은 그대로 받는다(이메일은 서버가 정규화한다).
 *
 * @param passwordMode {@code DIRECT}(관리자가 지금 입력) · {@code MAIL}(그 이메일로 「비밀번호 정하기」 링크)
 * @param password     {@code DIRECT} 일 때만 쓴다 — {@code MAIL} 이면 보내도 쓰지 않는다
 */
public record OperatorMemberCreateRequest(String email, String nickname, String passwordMode, String password) {

    /** 비밀번호·이메일을 싣지 않는다 */
    @Override
    public String toString() {
        return "OperatorMemberCreateRequest[email=(감춤), nickname=(감춤), passwordMode=" + passwordMode + ", password=(감춤)]";
    }
}
