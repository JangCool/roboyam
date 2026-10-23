package com.community.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.adminauth.service.AdminPasswordMatcher;
import com.community.audit.service.AuditLogService;
import com.community.mfa.dto.MfaStage;
import com.community.mfa.dto.PendingMfa;
import com.community.mfa.dto.request.MfaPasswordRequest;
import com.community.mfa.dto.request.MfaVerifyRequest;
import com.community.mfa.dto.response.MfaEnrollmentView;
import com.community.mfa.dto.response.MfaMeView;
import com.community.mfa.exception.MfaAlreadyEnrolledException;
import com.community.mfa.exception.MfaCodeInvalidException;
import com.community.mfa.exception.MfaEnrollmentNotStartedException;
import com.community.mfa.exception.MfaNotEnrolledException;
import com.community.mfa.exception.RecoveryCodeInvalidException;
import com.community.mfa.repository.master.MfaStore;
import com.community.mfa.service.MfaAccountService;
import com.community.mfa.service.MfaAttemptService;
import com.community.mfa.service.MfaCodeVerifier;
import com.community.mfa.service.MfaEnrollmentService;
import com.community.mfa.service.MfaLoginService;
import com.community.mfa.service.MfaSecrets;
import com.community.mfa.service.MfaVerifyService;
import com.community.mfa.service.TrustedDeviceService;
import com.community.mfa.totp.Base32;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.profile.exception.InvalidCurrentPasswordException;
import com.community.site.service.SiteAssets;
import com.community.zconfiguration.crypto.SecretEncryptor;
import com.community.zconfiguration.exception.CommonException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * OTP 등록·다시 등록·복구 코드 새로 만들기 — 비밀키 암호화 저장, 등록 중 10분, 다시 등록 확정 때 옛 비밀키·복구 코드·신뢰 기기 무효, 비밀값이 감사에 없음
 * (commerce-foundation-contract 「0-3」 API·데이터 규칙, 결정 4).
 */
class MfaEnrollmentTest extends MfaSpringTestBase {

    @Autowired
    private MfaAccountService accounts;
    @Autowired
    private MfaVerifyService verifier;
    @Autowired
    private TrustedDeviceService devices;
    @Autowired
    private MfaStore store;
    @Autowired
    private MfaCodeVerifier codeVerifier;
    @Autowired
    private MfaAttemptService attempts;
    @Autowired
    private MfaLoginService logins;
    @Autowired
    private OperatorRepository operators;
    @Autowired
    private AdminPasswordMatcher passwords;
    @Autowired
    private SiteAssets siteAssets;
    @Autowired
    private AuditLogService audit;

    @Nested
    class 등록 {

        @Test
        void 시작하면_비밀키와_otpauth_주소를_주고_DB_에는_암호문만_둔다() {
            long operator = newOperator();

            MfaEnrollmentView view = enrollments.startVoluntary(actor(operator));

            assertThat(view.secret()).matches("[A-Z2-7]{32}");
            assertThat(view.otpauthUri()).startsWith("otpauth://totp/").contains("secret=" + view.secret(), "algorithm=SHA1", "digits=6", "period=30");
            assertThat(view.accountName()).isEqualTo(email(operator));
            String pending = jdbc.queryForObject("SELECT pending_secret_enc FROM operator_mfa WHERE operator_id = ?", String.class, operator);
            assertThat(pending).startsWith("v1:").doesNotContain(view.secret());
            assertThat(Base32.encode(secrets.decrypt(pending))).isEqualTo(view.secret());
        }

        @Test
        void 확정하면_비밀키를_확정하고_복구_코드_10개를_준다_평문은_DB_에_없다() {
            long operator = newOperator();

            Enrolled enrolled = enrollViaService(operator);

            Map<String, Object> row = jdbc.queryForMap("SELECT secret_enc, enrolled_at, pending_secret_enc, last_step FROM operator_mfa WHERE operator_id = ?", operator);
            assertThat((String) row.get("secret_enc")).startsWith("v1:").doesNotContain(enrolled.secret());
            assertThat(row.get("enrolled_at")).isNotNull();
            assertThat(row.get("pending_secret_enc")).isNull();
            assertThat(row.get("last_step")).isNotNull(); // 확인에 쓴 칸 — 같은 코드로 곧바로 로그인하지 못한다
            assertThat(enrolled.recoveryCodes()).hasSize(10).allMatch(code -> code.matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
            assertThat(lastAudit(operator, "MFA_ENROLL")).contains("\"recoveryCodes\":10");
        }

        @Test
        void 확정에_쓴_코드로는_곧바로_로그인하지_못한다() {
            mfaOn();
            long operator = newOperator();
            AdminActor actor = actor(operator);
            MfaEnrollmentView view = enrollments.startVoluntary(actor);
            String code = codeNow(view.secret());
            enrollments.confirmVoluntary(actor, code);

            assertThatThrownBy(() -> verifier.verify(new PendingMfa(operator, MfaStage.VERIFY, clock.millis()), new MfaVerifyRequest(code, null, false), IP, null))
                    .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getErrorCode()).isEqualTo("MFA_CODE_REUSED"));
        }

        @Test
        void 틀린_코드로는_확정되지_않고_실패_횟수에_센다() {
            long operator = newOperator();
            AdminActor actor = actor(operator);
            MfaEnrollmentView view = enrollments.startVoluntary(actor);

            assertThatThrownBy(() -> enrollments.confirmVoluntary(actor, wrongCode(view.secret()))).isInstanceOf(MfaCodeInvalidException.class);
            assertThat(jdbc.queryForObject("SELECT secret_enc IS NULL FROM operator_mfa WHERE operator_id = ?", Boolean.class, operator)).isTrue();
            assertThat(lastAudit(operator, "MFA_FAILURE")).contains("\"stage\":\"ENROLL\"");
        }

        @Test
        void 이미_등록했으면_자발_등록은_409_MFA_ALREADY_ENROLLED() {
            long operator = newOperator();
            enrollDirect(operator);

            assertThatThrownBy(() -> enrollments.startVoluntary(actor(operator))).isInstanceOfSatisfying(MfaAlreadyEnrolledException.class,
                    e -> assertThat(e.getStatus().value()).isEqualTo(409));
        }

        @Test
        void 시작하지_않았으면_확정은_400_MFA_ENROLLMENT_NOT_STARTED() {
            long operator = newOperator();

            assertThatThrownBy(() -> enrollments.confirmVoluntary(actor(operator), "123456")).isInstanceOfSatisfying(MfaEnrollmentNotStartedException.class,
                    e -> assertThat(e.getStatus().value()).isEqualTo(400));
        }

        @Test
        void 등록_중_비밀키는_10분이_지나면_확정할_수_없다() {
            long operator = newOperator();
            AdminActor actor = actor(operator);
            MfaEnrollmentView view = enrollments.startVoluntary(actor);
            clock.advance(Duration.ofMinutes(10));

            assertThatThrownBy(() -> enrollments.confirmVoluntary(actor, codeNow(view.secret()))).isInstanceOf(MfaEnrollmentNotStartedException.class);
            assertThat(MfaEnrollmentService.PENDING_VALIDITY).isEqualTo(Duration.ofMinutes(10));
        }

        @Test
        void 등록_중_비밀키는_10분_안이면_확정된다() {
            long operator = newOperator();
            AdminActor actor = actor(operator);
            MfaEnrollmentView view = enrollments.startVoluntary(actor);
            clock.advance(Duration.ofMinutes(10).minusSeconds(1));

            assertThat(enrollments.confirmVoluntary(actor, codeNow(view.secret()))).hasSize(10);
        }

        @Test
        void 키가_없으면_등록_시작은_503_CRYPTO_KEY_MISSING_이고_아무것도_저장하지_않는다() {
            long operator = newOperator();
            MfaEnrollmentService noKey = new MfaEnrollmentService(store, new MfaSecrets(new SecretEncryptor("")), codeVerifier, attempts, devices, logins, operators,
                    passwords, siteAssets, ids, audit, clock);

            assertThatThrownBy(() -> noKey.startVoluntary(actor(operator))).isInstanceOfSatisfying(CommonException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo("CRYPTO_KEY_MISSING");
                assertThat(e.getStatus().value()).isEqualTo(503);
            });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_mfa WHERE operator_id = ?", Integer.class, operator)).isZero();
        }
    }

    @Nested
    class 다시_등록 {

        @Test
        void 비밀번호가_틀리면_400_INVALID_CURRENT_PASSWORD_이고_실패_감사를_남긴다() {
            long operator = newOperator();
            enrollDirect(operator);

            assertThatThrownBy(() -> enrollments.reenroll(actor(operator), new MfaPasswordRequest("wrong-password-1"))).isInstanceOfSatisfying(
                    InvalidCurrentPasswordException.class, e -> assertThat(e.getStatus().value()).isEqualTo(400));
            assertThat(lastAudit(operator, "MFA_REENROLL")).contains("\"reason\":\"CURRENT_PASSWORD_MISMATCH\"");
        }

        @Test
        void 확정하기_전에는_옛_비밀키가_그대로_쓰인다() {
            mfaOn();
            long operator = newOperator();
            Enrolled old = enrollViaService(operator);
            enrollments.reenroll(actor(operator), new MfaPasswordRequest(PASSWORD));

            verifier.verify(new PendingMfa(operator, MfaStage.VERIFY, clock.millis()), new MfaVerifyRequest(nextCode(old.secret()), null, false), IP, null);
        }

        @Test
        void 확정하면_옛_비밀키_복구_코드_신뢰_기기가_모두_무효가_된다() {
            mfaOn();
            long operator = newOperator();
            Enrolled old = enrollViaService(operator);
            String token = devices.trust(operator, 30, IP, null);
            AdminActor actor = actor(operator);

            MfaEnrollmentView view = enrollments.reenroll(actor, new MfaPasswordRequest(PASSWORD));
            List<String> fresh = enrollments.confirmVoluntary(actor, nextCode(view.secret()));

            PendingMfa pending = new PendingMfa(operator, MfaStage.VERIFY, clock.millis());
            assertThatThrownBy(() -> verifier.verify(pending, new MfaVerifyRequest(nextCode(old.secret()), null, false), IP, null))
                    .isInstanceOf(MfaCodeInvalidException.class);
            assertThatThrownBy(() -> verifier.verify(pending, new MfaVerifyRequest(null, old.recoveryCodes().get(0), false), IP, null))
                    .isInstanceOf(RecoveryCodeInvalidException.class);
            assertThat(devices.isTrusted(operator, token)).isFalse();
            assertThat(deviceCount(operator)).isZero();
            assertThat(fresh).hasSize(10).doesNotContainAnyElementsOf(old.recoveryCodes());
            assertThat(lastAudit(operator, "MFA_REENROLL")).contains("\"trustedDevicesRevoked\":1");
            assertThat(lastAudit(operator, "MFA_TRUSTED_DEVICE_REVOKE")).contains("\"cause\":\"REENROLL\"");
            verifier.verify(pending, new MfaVerifyRequest(nextCode(view.secret()), null, false), IP, null); // 새 비밀키는 된다
        }
    }

    @Nested
    class 복구_코드_새로_만들기 {

        @Test
        void 현재_OTP_로_새_10개를_만들고_옛_코드는_모두_무효() {
            mfaOn();
            long operator = newOperator();
            Enrolled old = enrollViaService(operator);

            List<String> fresh = accounts.regenerateRecoveryCodes(actor(operator), nextCode(old.secret()));

            assertThat(fresh).hasSize(10).doesNotContainAnyElementsOf(old.recoveryCodes());
            assertThatThrownBy(() -> verifier.verify(new PendingMfa(operator, MfaStage.VERIFY, clock.millis()), new MfaVerifyRequest(null, old.recoveryCodes().get(0), false),
                    IP, null)).isInstanceOf(RecoveryCodeInvalidException.class);
            assertThat(lastAudit(operator, "MFA_RECOVERY_CODES_REGENERATE")).isNotNull();
        }

        @Test
        void 등록하지_않았으면_409_MFA_NOT_ENROLLED() {
            long operator = newOperator();

            assertThatThrownBy(() -> accounts.regenerateRecoveryCodes(actor(operator), "123456")).isInstanceOf(MfaNotEnrolledException.class);
        }
    }

    @Test
    void 내_상태는_사이트_사용_등록_남은_복구_코드_유예_마감_신뢰_일수() {
        settings(true, 14, 2, 5, clock.instant().minus(Duration.ofDays(1)));
        long unenrolled = newOperator(clock.instant().minus(Duration.ofDays(30)));
        MfaMeView before = accounts.me(actor(unenrolled));
        assertThat(before.siteEnabled()).isTrue();
        assertThat(before.enrolled()).isFalse();
        assertThat(before.recoveryCodesLeft()).isZero();
        assertThat(before.graceEndsAt()).isNotNull();
        assertThat(before.trustDays()).isEqualTo(60);

        Enrolled enrolled = enrollViaService(unenrolled);
        verifier.verify(new PendingMfa(unenrolled, MfaStage.VERIFY, clock.millis()), new MfaVerifyRequest(null, enrolled.recoveryCodes().get(0), false), IP, null);
        MfaMeView after = accounts.me(actor(unenrolled));
        assertThat(after.enrolled()).isTrue();
        assertThat(after.enrolledAt()).isNotNull();
        assertThat(after.lastUsedAt()).isNotNull();
        assertThat(after.recoveryCodesLeft()).isEqualTo(9);
        assertThat(after.graceEndsAt()).isNull();
    }

    @Test
    void 비밀키_코드_복구_코드_신뢰_기기_쿠키는_감사_기록에_없다() {
        mfaOn();
        long operator = newOperator();
        List<String> secretsSeen = new ArrayList<>();
        Enrolled enrolled = enrollViaService(operator);
        secretsSeen.add(enrolled.secret());
        secretsSeen.addAll(enrolled.recoveryCodes());
        enrolled.recoveryCodes().forEach(code -> secretsSeen.add(code.replace("-", "")));
        String wrong = wrongCode(enrolled.secret());
        secretsSeen.add(wrong);
        assertThatThrownBy(() -> verifier.verify(new PendingMfa(operator, MfaStage.VERIFY, clock.millis()), new MfaVerifyRequest(wrong, null, false), IP, null))
                .isInstanceOf(MfaCodeInvalidException.class);
        String good = nextCode(enrolled.secret());
        secretsSeen.add(good);
        String token = verifier.verify(new PendingMfa(operator, MfaStage.VERIFY, clock.millis()), new MfaVerifyRequest(good, null, true), IP, null).trustToken();
        secretsSeen.add(token);
        secretsSeen.add(jdbc.queryForObject("SELECT secret_enc FROM operator_mfa WHERE operator_id = ?", String.class, operator));

        String details = String.join("\n", jdbc.queryForList("SELECT coalesce(detail, '') FROM audit_logs WHERE actor_id = ? OR target_id = ?", String.class, operator,
                Long.toString(operator)));
        assertThat(details).isNotBlank();
        for (String secret : secretsSeen) {
            assertThat(details).as("감사 detail 에 비밀값이 없다").doesNotContain(secret);
        }
    }
}
