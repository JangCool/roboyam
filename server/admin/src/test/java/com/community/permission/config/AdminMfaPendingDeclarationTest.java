package com.community.permission.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.mfa.dto.MfaStage;
import com.community.permission.annotation.AdminAuthenticated;
import com.community.permission.annotation.AdminMfaPending;
import com.community.permission.annotation.AdminPublic;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

/**
 * {@code @AdminMfaPending} 이 접근 규칙 선언으로 인정되고(정확히 하나 — 다른 선언과 섞이면 거절), 기동 검사({@link AdminAccessVerifier})도 같은 판정을 쓴다
 * (commerce-foundation-contract 「0-3」 — 대기 세션 전용 선언).
 */
class AdminMfaPendingDeclarationTest {

    static class Sample {

        @AdminMfaPending(MfaStage.VERIFY)
        public void pendingOnly() {
        }

        @AdminMfaPending(MfaStage.ENROLL)
        @AdminAuthenticated
        public void mixed() {
        }

        @AdminMfaPending({MfaStage.VERIFY, MfaStage.ENROLL})
        @AdminPublic
        public void mixedPublic() {
        }

        public void undeclared() {
        }
    }

    @AdminMfaPending(MfaStage.ENROLL)
    static class PendingClass {

        public void inherited() {
        }
    }

    private static HandlerMethod handler(Object bean, String name) throws NoSuchMethodException {
        return new HandlerMethod(bean, bean.getClass().getMethod(name));
    }

    @Test
    void 대기_선언만_있으면_MFA_PENDING() throws Exception {
        assertThat(AdminAccessInterceptor.kindOf(handler(new Sample(), "pendingOnly"))).isEqualTo(AdminAccessInterceptor.Kind.MFA_PENDING);
        assertThat(AdminAccessInterceptor.kindOf(handler(new PendingClass(), "inherited"))).isEqualTo(AdminAccessInterceptor.Kind.MFA_PENDING);
    }

    @Test
    void 다른_선언과_섞이면_거절된다() throws Exception {
        assertThat(AdminAccessInterceptor.kindOf(handler(new Sample(), "mixed"))).isEqualTo(AdminAccessInterceptor.Kind.NONE);
        assertThat(AdminAccessInterceptor.kindOf(handler(new Sample(), "mixedPublic"))).isEqualTo(AdminAccessInterceptor.Kind.NONE);
        assertThat(AdminAccessInterceptor.kindOf(handler(new Sample(), "undeclared"))).isEqualTo(AdminAccessInterceptor.Kind.NONE);
    }
}
