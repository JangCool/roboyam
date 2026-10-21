package com.community.operator.dto;

import java.time.Instant;

/**
 * 운영자 초대를 만들었다는 알림 — 트랜잭션이 <b>커밋된 뒤에만</b> 듣는 쪽({@code OperatorMailSender})이 초대 링크를 이메일로 보낸다.
 * 토큰 원문은 이 알림에만 있고 DB 에는 해시만 남는다. {@link #toString()} 은 값을 싣지 않는다.
 */
public record OperatorInvitationIssued(String email, String token, Instant expiresAt) {

    @Override
    public String toString() {
        return "OperatorInvitationIssued[email=(감춤), token=(감춤), expiresAt=" + expiresAt + "]";
    }
}
