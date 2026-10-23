package com.community.mfa.dto.response;

import com.community.mfa.dto.MfaStage;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 로그인 응답·{@code me} 의 2차 인증 단계({@code mfa}). 없으면(null) 2차 인증 없이 바로 들어간 것이다.
 *
 * @param graceEndsAt GRACE 의 유예 마감(그 밖은 칸이 나가지 않는다)
 * @param trustDays   「이 기기에서 다시 묻지 않기」 일수(설정 개월 × 30)
 */
public record AdminLoginMfa(MfaStage stage, @JsonInclude(JsonInclude.Include.NON_NULL) Instant graceEndsAt, int trustDays) {
}
