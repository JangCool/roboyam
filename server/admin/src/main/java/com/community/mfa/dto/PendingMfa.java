package com.community.mfa.dto;

import java.io.Serializable;

/**
 * 「2차 대기」 세션에 두는 값 — 비밀번호는 맞았지만 2차 인증이 남은 상태. 세션이 DB(Spring Session JDBC)에 직렬화되므로 가볍고 {@link Serializable} 이어야 한다.
 * 보안 컨텍스트(로그인 주체)가 아니므로 이 세션은 <b>로그인으로 취급되지 않는다</b>. 비밀값은 없다.
 *
 * @param operatorId      비밀번호가 맞은 운영자
 * @param stage           남은 단계(VERIFY · ENROLL)
 * @param createdAtMillis 대기를 만든 시각(epoch ms) — 5분이 지나면 끝난다
 */
public record PendingMfa(long operatorId, MfaStage stage, long createdAtMillis) implements Serializable {

    private static final long serialVersionUID = 1L;
}
