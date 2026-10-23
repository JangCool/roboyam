package com.community.permission.annotation;

import com.community.mfa.dto.MfaStage;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 「2차 대기」 세션(비밀번호는 맞았지만 2차 인증이 남은 상태 — 로그인 아님)으로만 부를 수 있는 API. 허용하는 단계를 {@link #value()} 로 정한다.
 * 대기 세션이 없거나 5분이 지났거나 그 단계가 아니면 {@code 401 MFA_PENDING_EXPIRED}. 컨트롤러는 대기 정보({@code PendingMfa})를 인자로 받는다.
 *
 * <p>{@link RequirePermission} · {@link AdminAuthenticated} · {@link AdminPublic} 과 함께 쓰지 않는다(접근 규칙은 정확히 하나). 로그인 세션으로는 부를 수 없다 —
 * 로그인한 운영자는 대기 정보가 없다. {@code SecurityConfig} 의 로그인 전 허용 주소와 짝이다(CSRF 는 그대로 필요하다).
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminMfaPending {

    /** 허용하는 대기 단계(VERIFY · ENROLL) */
    MfaStage[] value();
}
