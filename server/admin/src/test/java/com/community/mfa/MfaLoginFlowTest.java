package com.community.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.adminauth.controller.AdminAccountController;
import com.community.adminauth.controller.AdminAuthController;
import com.community.adminauth.controller.AdminSession;
import com.community.adminauth.dto.request.AdminLoginRequest;
import com.community.adminauth.dto.request.PasswordResetRequest;
import com.community.adminauth.dto.response.AdminMeResponse;
import com.community.mfa.controller.AdminMfaAuthController;
import com.community.mfa.controller.AdminMfaController;
import com.community.mfa.controller.MfaTrustCookie;
import com.community.mfa.controller.OperatorMfaController;
import com.community.mfa.dto.MfaStage;
import com.community.mfa.dto.PendingMfa;
import com.community.mfa.dto.request.MfaCodeRequest;
import com.community.mfa.dto.request.MfaVerifyRequest;
import com.community.mfa.dto.response.MfaEnrollmentConfirmResponse;
import com.community.mfa.dto.response.MfaEnrollmentView;
import com.community.permission.config.AdminAccessInterceptor;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.crypto.Digests;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.method.HandlerMethod;

/**
 * 비밀번호가 맞은 뒤의 흐름 — 상태 판정 표 5줄, 「2차 대기」 세션이 로그인이 아님(인터셉터), 대기 5분, 통과하면 세션 ID 를 바꿔 로그인, LOGIN_SUCCESS 는 세션이 완성될 때
 * (commerce-foundation-contract 「0-3」 상태 판정·API). 컨트롤러·세션 도우미·인터셉터를 실제 빈으로 부르고, 요청은 목 서블릿 요청으로 흉내 낸다(세션은 요청 사이에 이어 준다).
 */
class MfaLoginFlowTest extends MfaSpringTestBase {

    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 Version/17.0 Safari/605.1.15";

    @Autowired
    private AdminAuthController authController;
    @Autowired
    private AdminAccountController accountController;
    @Autowired
    private AdminMfaAuthController mfaAuthController;
    @Autowired
    private AdminMfaController mfaController;
    @Autowired
    private OperatorMfaController operatorMfaController;
    @Autowired
    private AdminAccessInterceptor interceptor;
    @Autowired
    private AdminSession adminSession;
    @Autowired
    private SecurityContextRepository contextRepository;

    /** 같은 브라우저의 다음 요청 — 세션을 잇고, 보안 필터가 하듯 세션의 보안 컨텍스트를 읽어 둔다 */
    private MockHttpServletRequest next(HttpSession session, Cookie... cookies) {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(IP);
        request.addHeader("User-Agent", UA);
        if (session != null) {
            request.setSession(session);
        }
        if (cookies.length > 0) {
            request.setCookies(cookies);
        }
        SecurityContextHolder.setContext(contextRepository.loadDeferredContext(request).get());
        return request;
    }

    private record Step(MockHttpServletRequest request, MockHttpServletResponse response, AdminMeResponse body) {
        HttpSession session() {
            return request.getSession(false);
        }
    }

    private Step login(long operatorId, Cookie... cookies) {
        MockHttpServletRequest request = next(null, cookies);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseEntity<ApiResponse<AdminMeResponse>> entity = authController.login(new AdminLoginRequest(email(operatorId), PASSWORD), request, response);
        return new Step(request, response, entity.getBody().data());
    }

    private AdminMeResponse me(HttpSession session) {
        return authController.me(next(session)).getBody().data();
    }

    private HandlerMethod handler(Object bean, String name, Class<?>... types) throws NoSuchMethodException {
        return new HandlerMethod(bean, bean.getClass().getMethod(name, types));
    }

    @Nested
    class 상태_판정_표 {

        @Test
        void 사이트_꺼짐이면_등록했어도_바로_로그인_mfa_없음() {
            mfaOff();
            long operator = newOperator();
            enrollDirect(operator);

            Step step = login(operator);

            assertThat(step.body().authenticated()).isTrue();
            assertThat(step.body().mfa()).isNull();
            assertThat(lastAudit(operator, "LOGIN_SUCCESS")).contains("\"mfa\":\"OFF\"", "\"agent\":\"Safari / macOS / Desktop\"");
            assertThat(jdbc.queryForObject("SELECT last_login_at IS NOT NULL FROM operators WHERE id = ?", Boolean.class, operator)).isTrue();
        }

        @Test
        void 켜짐_등록됨_유효한_신뢰_기기_쿠키면_바로_로그인() {
            mfaOn();
            long operator = newOperator();
            String secret = enrollDirect(operator);
            Step first = login(operator);
            MockHttpServletResponse verified = new MockHttpServletResponse();
            mfaAuthController.verify(pendingOf(first.session()), new MfaVerifyRequest(codeNow(secret), null, true), next(first.session()), verified);
            Cookie trust = verified.getCookie(MfaTrustCookie.NAME);
            assertThat(trust).isNotNull();
            assertThat(trust.isHttpOnly()).isTrue();
            assertThat(trust.getPath()).isEqualTo("/api/admin");
            assertThat(trust.getMaxAge()).isEqualTo((int) Duration.ofDays(30).toSeconds());
            assertThat(verified.getHeader("Set-Cookie")).contains("SameSite=Strict");

            Step again = login(operator, new Cookie(MfaTrustCookie.NAME, trust.getValue()));

            assertThat(again.body().authenticated()).isTrue();
            assertThat(again.body().mfa()).isNull();
            assertThat(lastAudit(operator, "LOGIN_SUCCESS")).contains("\"mfa\":\"TRUSTED_DEVICE\"");
        }

        @Test
        void 켜짐_등록됨이면_VERIFY_대기이고_로그인이_아니다() {
            mfaOn();
            long operator = newOperator();
            enrollDirect(operator);

            Step step = login(operator);

            assertThat(step.body().authenticated()).isFalse();
            assertThat(step.body().user()).isNull();
            assertThat(step.body().mfa().stage()).isEqualTo(MfaStage.VERIFY);
            assertThat(step.body().mfa().trustDays()).isEqualTo(30);
            assertThat(step.body().mfa().graceEndsAt()).isNull();
            assertThat(adminSession.resolve(next(step.session()))).isEmpty();
            assertThat(auditCount(operator, "LOGIN_SUCCESS")).isZero(); // 세션이 완성될 때 남긴다
            assertThat(jdbc.queryForObject("SELECT last_login_at IS NULL FROM operators WHERE id = ?", Boolean.class, operator)).isTrue();
        }

        @Test
        void 켜짐_미등록_유예_안이면_로그인하고_GRACE_안내() {
            settings(true, 14, 1, 5, clock.instant().minus(Duration.ofDays(3)));
            long operator = newOperator(clock.instant().minus(Duration.ofDays(100)));

            Step step = login(operator);

            assertThat(step.body().authenticated()).isTrue();
            assertThat(step.body().mfa().stage()).isEqualTo(MfaStage.GRACE);
            assertThat(step.body().mfa().graceEndsAt()).isNotNull();
            assertThat(lastAudit(operator, "LOGIN_SUCCESS")).contains("\"mfa\":\"GRACE\"");
        }

        @Test
        void 켜짐_미등록_유예_지남이면_ENROLL_대기_등록해야_로그인() {
            settings(true, 2, 1, 5, clock.instant().minus(Duration.ofDays(3)));
            long operator = newOperator(clock.instant().minus(Duration.ofDays(100)));

            Step step = login(operator);
            assertThat(step.body().authenticated()).isFalse();
            assertThat(step.body().mfa().stage()).isEqualTo(MfaStage.ENROLL);

            PendingMfa pending = pendingOf(step.session());
            MfaEnrollmentView view = mfaAuthController.startEnrollment(pending).getBody().data();
            MockHttpServletRequest confirmRequest = next(step.session());
            MfaEnrollmentConfirmResponse confirmed = mfaAuthController.confirmEnrollment(pending, new MfaCodeRequest(codeNow(view.secret())), confirmRequest,
                    new MockHttpServletResponse()).getBody().data();

            assertThat(confirmed.recoveryCodes()).hasSize(10);
            assertThat(confirmed.me().authenticated()).isTrue();
            assertThat(lastAudit(operator, "LOGIN_SUCCESS")).contains("\"mfa\":\"ENROLLED\"");
            assertThat(lastAudit(operator, "MFA_ENROLL")).isNotNull();
            assertThat(view.issuer()).isNotBlank();
            assertThat(view.accountName()).isEqualTo(email(operator));
        }
    }

    @Nested
    class 대기_세션 {

        @Test
        void 새로고침해도_me_는_비로그인과_같은_단계를_준다() {
            mfaOn();
            long operator = newOperator();
            enrollDirect(operator);
            Step step = login(operator);

            AdminMeResponse me = me(step.session());

            assertThat(me.authenticated()).isFalse();
            assertThat(me.mfa().stage()).isEqualTo(MfaStage.VERIFY);
        }

        @Test
        void 로그인이_필요한_API_는_401_이고_대기_API_만_통과한다() throws Exception {
            mfaOn();
            long operator = newOperator();
            enrollDirect(operator);
            HttpSession session = login(operator).session();

            assertThatThrownBy(() -> interceptor.preHandle(next(session), new MockHttpServletResponse(), handler(mfaController, "me", AdminActor.class)))
                    .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getErrorCode()).isEqualTo("UNAUTHENTICATED"));
            assertThatThrownBy(() -> interceptor.preHandle(next(session), new MockHttpServletResponse(), handler(operatorMfaController, "get", String.class)))
                    .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getErrorCode()).isEqualTo("UNAUTHENTICATED"));

            MockHttpServletRequest verifyRequest = next(session);
            boolean passed = interceptor.preHandle(verifyRequest, new MockHttpServletResponse(),
                    handler(mfaAuthController, "verify", PendingMfa.class, MfaVerifyRequest.class, jakarta.servlet.http.HttpServletRequest.class,
                            jakarta.servlet.http.HttpServletResponse.class));
            assertThat(passed).isTrue();
            assertThat(verifyRequest.getAttribute(AdminAccessInterceptor.MFA_PENDING_ATTRIBUTE)).isInstanceOfSatisfying(PendingMfa.class,
                    p -> assertThat(p.operatorId()).isEqualTo(operator));
            assertThat(verifyRequest.getAttribute(AdminAccessInterceptor.ACTOR_ATTRIBUTE)).isNull();
        }

        @Test
        void 다른_단계의_대기_API_는_MFA_PENDING_EXPIRED() {
            mfaOn();
            long operator = newOperator();
            enrollDirect(operator);
            HttpSession session = login(operator).session();

            assertThatThrownBy(() -> interceptor.preHandle(next(session), new MockHttpServletResponse(), handler(mfaAuthController, "startEnrollment", PendingMfa.class)))
                    .isInstanceOfSatisfying(CommonException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo("MFA_PENDING_EXPIRED");
                        assertThat(e.getStatus().value()).isEqualTo(401);
                    });
        }

        @Test
        void 대기_세션이_없으면_대기_API_도_MFA_PENDING_EXPIRED() {
            assertThatThrownBy(() -> interceptor.preHandle(next(null), new MockHttpServletResponse(), handler(mfaAuthController, "startEnrollment", PendingMfa.class)))
                    .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getErrorCode()).isEqualTo("MFA_PENDING_EXPIRED"));
        }

        @Test
        void 대기는_5분_동안만_유효하다() {
            mfaOn();
            long operator = newOperator();
            enrollDirect(operator);
            HttpSession session = login(operator).session();

            clock.advance(Duration.ofMinutes(5).minusSeconds(1));
            assertThat(adminSession.mfaPending(next(session))).isPresent();

            clock.advance(Duration.ofSeconds(2));
            assertThat(adminSession.mfaPending(next(session))).isEmpty();
            assertThat(AdminSession.MFA_PENDING_TTL).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        void 만료된_대기로_verify_를_부르면_401_MFA_PENDING_EXPIRED() {
            mfaOn();
            long operator = newOperator();
            enrollDirect(operator);
            HttpSession session = login(operator).session();
            clock.advance(Duration.ofMinutes(6));

            assertThatThrownBy(() -> interceptor.preHandle(next(session), new MockHttpServletResponse(),
                    handler(mfaAuthController, "verify", PendingMfa.class, MfaVerifyRequest.class, jakarta.servlet.http.HttpServletRequest.class,
                            jakarta.servlet.http.HttpServletResponse.class)))
                    .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getErrorCode()).isEqualTo("MFA_PENDING_EXPIRED"));
        }

        @Test
        void 대기_중에_계정이_로그인할_수_없게_되면_대기가_끝난다() {
            mfaOn();
            long operator = newOperator();
            enrollDirect(operator);
            HttpSession session = login(operator).session();

            jdbc.update("UPDATE operators SET locked = true WHERE id = ?", operator);

            assertThat(adminSession.mfaPending(next(session))).isEmpty();
        }

        @Test
        void 통과하면_세션_ID_를_바꿔_로그인_세션을_만든다() {
            mfaOn();
            long operator = newOperator();
            String secret = enrollDirect(operator);
            Step step = login(operator);
            HttpSession pendingSession = step.session();
            String pendingId = pendingSession.getId();

            MockHttpServletRequest request = next(step.session());
            AdminMeResponse body = mfaAuthController.verify(pendingOf(step.session()), new MfaVerifyRequest(codeNow(secret), null, false), request,
                    new MockHttpServletResponse()).getBody().data();

            assertThat(body.authenticated()).isTrue();
            assertThat(body.user().id()).isEqualTo(Long.toString(operator));
            assertThat(request.getSession(false).getId()).isNotEqualTo(pendingId);
            assertThat(((MockHttpSession) pendingSession).isInvalid()).isTrue();
            assertThat(adminSession.mfaPending(next(request.getSession(false)))).isEmpty();
            assertThat(adminSession.resolve(next(request.getSession(false)))).map(AdminActor::operatorId).contains(operator);
        }

        @Test
        void 복구_코드로_통과하면_응답에_남은_개수가_실린다() {
            mfaOn();
            long operator = newOperator();
            Enrolled enrolled = enrollViaService(operator);
            Step step = login(operator);

            AdminMeResponse body = mfaAuthController.verify(pendingOf(step.session()), new MfaVerifyRequest(null, enrolled.recoveryCodes().get(3), false), next(step.session()),
                    new MockHttpServletResponse()).getBody().data();

            assertThat(body.authenticated()).isTrue();
            assertThat(body.recoveryCodesLeft()).isEqualTo(9);
        }

        @Test
        void 대기_취소는_대기_세션만_버리고_로그인_세션은_건드리지_않는다() {
            mfaOn();
            long operator = newOperator();
            enrollDirect(operator);
            HttpSession pending = login(operator).session();

            mfaAuthController.cancel(next(pending));
            assertThat(((MockHttpSession) pending).isInvalid()).isTrue();

            mfaOff();
            HttpSession loggedIn = login(operator).session();
            mfaAuthController.cancel(next(loggedIn));
            assertThat(adminSession.resolve(next(loggedIn))).isPresent();
        }

        @Test
        void 다른_운영자의_신뢰_기기_쿠키로는_바로_로그인하지_않는다() {
            mfaOn();
            long owner = newOperator();
            String secret = enrollDirect(owner);
            Step first = login(owner);
            MockHttpServletResponse verified = new MockHttpServletResponse();
            mfaAuthController.verify(pendingOf(first.session()), new MfaVerifyRequest(codeNow(secret), null, true), next(first.session()), verified);
            long other = newOperator();
            enrollDirect(other);

            Step step = login(other, new Cookie(MfaTrustCookie.NAME, verified.getCookie(MfaTrustCookie.NAME).getValue()));

            assertThat(step.body().authenticated()).isFalse();
            assertThat(step.body().mfa().stage()).isEqualTo(MfaStage.VERIFY);
        }
    }

    @Test
    void 비밀번호_단계_성공은_비밀번호_실패_횟수만_지운다() {
        mfaOn();
        long operator = newOperator();
        enrollDirect(operator);
        String key = Digests.sha256Hex("admin:" + email(operator));
        jdbc.update("INSERT INTO login_attempts (email_key, fail_count, updated_at) VALUES (?, 3, now())", key);

        login(operator);

        assertThat(jdbc.queryForObject("SELECT fail_count FROM login_attempts WHERE email_key = ?", Integer.class, key)).isZero();
        assertThat(auditCount(operator, "LOGIN_SUCCESS")).isZero();
    }

    @Test
    void 비밀번호_재설정으로_들어와도_2차_인증_판정을_거치고_신뢰_기기는_끊긴다() {
        mfaOn();
        long operator = newOperator();
        String secret = enrollDirect(operator);
        jdbc.update("INSERT INTO operator_trusted_devices (id, operator_id, token_hash, trusted_at, expires_at) VALUES (?, ?, ?, now(), now() + interval '30 days')",
                ids.nextId(), operator, Digests.sha256Hex("reset-device-" + operator));
        String salt = "s".repeat(32);
        jdbc.update("INSERT INTO operator_cert_codes (id, email_key, operator_id, code_salt, code_hash, expires_at) VALUES (?, ?, ?, ?, ?, now() + interval '10 minutes')",
                ids.nextId(), Digests.sha256Hex("admin-cert:" + email(operator)), operator, salt, Digests.sha256Hex(salt + ":123456"));

        MockHttpServletRequest request = next(null);
        AdminMeResponse body = accountController.resetPassword(new PasswordResetRequest(email(operator), "123456", "NewPassw0rd-reset"), request,
                new MockHttpServletResponse()).getBody().data();

        assertThat(body.authenticated()).isFalse();
        assertThat(body.mfa().stage()).isEqualTo(MfaStage.VERIFY);
        assertThat(deviceCount(operator)).isZero();
        assertThat(lastAudit(operator, "MFA_TRUSTED_DEVICE_REVOKE")).contains("\"cause\":\"PASSWORD_RESET\"");

        mfaAuthController.verify(pendingOf(request.getSession(false)), new MfaVerifyRequest(codeNow(secret), null, false), next(request.getSession(false)),
                new MockHttpServletResponse());
        assertThat(lastAudit(operator, "LOGIN_SUCCESS")).contains("\"mfa\":\"TOTP\"");
    }

    private PendingMfa pendingOf(HttpSession session) {
        Optional<PendingMfa> pending = adminSession.mfaPending(next(session));
        assertThat(pending).as("2차 대기 세션").isPresent();
        return pending.get();
    }

}
