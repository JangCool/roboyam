package com.community.operator.dto;

/**
 * 운영자 계정을 바로 만들었다는 알림 — 트랜잭션이 <b>커밋된 뒤에만</b> 듣는 쪽({@code OperatorMailSender})이 임시 비밀번호를 이메일로 안내한다.
 * 임시 비밀번호는 이 알림에만 있고 DB·응답·로그에 남지 않는다. {@link #toString()} 은 값을 싣지 않는다.
 */
public record OperatorCredentialsIssued(String email, String operatorName, String temporaryPassword) {

    @Override
    public String toString() {
        return "OperatorCredentialsIssued[email=(감춤), operatorName=(감춤), temporaryPassword=(감춤)]";
    }
}
