package com.community.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.mfa.dto.MfaLoginMethod;
import com.community.mfa.dto.MfaStage;
import com.community.mfa.dto.MfaVerifyResult;
import com.community.mfa.dto.PendingMfa;
import com.community.mfa.dto.request.MfaVerifyRequest;
import com.community.mfa.exception.MfaCodeInvalidException;
import com.community.mfa.exception.MfaCodeReusedException;
import com.community.mfa.exception.MfaLockedException;
import com.community.mfa.exception.RecoveryCodeInvalidException;
import com.community.mfa.service.MfaAccountService;
import com.community.mfa.service.MfaAttemptService;
import com.community.mfa.service.MfaVerifyService;
import com.community.mfa.service.TrustedDeviceService;
import com.community.zconfiguration.crypto.Digests;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 로그인 2단계(VERIFY) — OTP·복구 코드·실패 제한·신뢰 기기 (commerce-foundation-contract 「0-3」 데이터·보안 규칙, commerce.md 13-3).
 */
class MfaVerifyTest extends MfaSpringTestBase {

    @Autowired
    private MfaVerifyService verifier;
    @Autowired
    private MfaAccountService accounts;
    @Autowired
    private TrustedDeviceService devices;

    private PendingMfa pending(long operatorId) {
        return new PendingMfa(operatorId, MfaStage.VERIFY, clock.millis());
    }

    private MfaVerifyResult otp(long operatorId, String code, boolean remember) {
        return verifier.verify(pending(operatorId), new MfaVerifyRequest(code, null, remember), IP, "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0 Safari/537.36");
    }

    private MfaVerifyResult recovery(long operatorId, String code) {
        return verifier.verify(pending(operatorId), new MfaVerifyRequest(null, code, false), IP, null);
    }

    @Nested
    class OTP {

        @Test
        void 맞는_코드면_통과하고_로그인_완성_감사에_TOTP_를_남긴다() {
            mfaOn();
            long operator = newOperator();
            String secret = enrollDirect(operator);

            MfaVerifyResult result = otp(operator, codeNow(secret), false);

            assertThat(result.method()).isEqualTo(MfaLoginMethod.TOTP);
            assertThat(result.trustToken()).isNull();
            assertThat(lastAudit(operator, "LOGIN_SUCCESS")).contains("\"mfa\":\"TOTP\"", "\"agent\":\"Chrome / Windows / Desktop\"");
            assertThat(jdbc.queryForObject("SELECT last_login_at IS NOT NULL FROM operators WHERE id = ?", Boolean.class, operator)).isTrue();
        }

        @Test
        void 같은_코드를_다시_내면_MFA_CODE_REUSED() {
            mfaOn();
            long operator = newOperator();
            String secret = enrollDirect(operator);
            String code = codeNow(secret);
            otp(operator, code, false);

            assertThatThrownBy(() -> otp(operator, code, false)).isInstanceOf(MfaCodeReusedException.class);
            assertThat(lastAudit(operator, "MFA_FAILURE")).contains("\"reason\":\"CODE_REUSED\"");
        }

        @Test
        void 받아들인_칸보다_이른_칸의_코드도_MFA_CODE_REUSED() {
            mfaOn();
            long operator = newOperator();
            String secret = enrollDirect(operator);
            String previous = codeNow(secret);
            otp(operator, nextCode(secret), false); // 다음 칸을 받아들였다

            assertThatThrownBy(() -> otp(operator, previous, false)).isInstanceOf(MfaCodeReusedException.class);
        }

        @Test
        void 틀리면_MFA_CODE_INVALID_와_남은_횟수() {
            mfaOn();
            long operator = newOperator();
            String secret = enrollDirect(operator);

            assertThatThrownBy(() -> otp(operator, wrongCode(secret), false)).isInstanceOfSatisfying(MfaCodeInvalidException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo("MFA_CODE_INVALID");
                assertThat(e.getRemainingAttempts()).isEqualTo(4);
                assertThat(e.getStatus().value()).isEqualTo(400);
            });
            assertThat(lastAudit(operator, "MFA_FAILURE")).contains("\"reason\":\"CODE_INVALID\"", "\"stage\":\"LOGIN\"");
        }

        @Test
        void 숫자_6자리가_아니면_VALIDATION_이고_횟수에_세지_않는다() {
            mfaOn();
            long operator = newOperator();
            enrollDirect(operator);

            assertThatThrownBy(() -> otp(operator, "12a456", false)).isInstanceOfSatisfying(ValidationException.class,
                    e -> assertThat(e.getFields()).containsKey("code"));
            assertThat(failCount(operator)).isZero();
        }

        @Test
        void 코드와_복구_코드를_함께_내거나_둘_다_비우면_VALIDATION() {
            mfaOn();
            long operator = newOperator();
            enrollDirect(operator);

            assertThatThrownBy(() -> verifier.verify(pending(operator), new MfaVerifyRequest("123456", "ABCDE-FGHJK", false), IP, null))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> verifier.verify(pending(operator), new MfaVerifyRequest(" ", null, false), IP, null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class 실패_제한 {

        @Test
        void 한도만큼_틀리면_그_실패부터_MFA_LOCKED_이고_잠금은_관리자_로그인_잠금_시간이다() {
            settings(true, 14, 1, 3, clock.instant().minus(Duration.ofDays(1)));
            long operator = newOperator();
            String secret = enrollDirect(operator);

            assertThatThrownBy(() -> otp(operator, wrongCode(secret), false)).isInstanceOfSatisfying(MfaCodeInvalidException.class,
                    e -> assertThat(e.getRemainingAttempts()).isEqualTo(2));
            assertThatThrownBy(() -> otp(operator, wrongCode(secret), false)).isInstanceOfSatisfying(MfaCodeInvalidException.class,
                    e -> assertThat(e.getRemainingAttempts()).isEqualTo(1));
            assertThatThrownBy(() -> otp(operator, wrongCode(secret), false)).isInstanceOfSatisfying(MfaLockedException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo("MFA_LOCKED");
                assertThat(e.getStatus().value()).isEqualTo(429);
                assertThat(e.getRetryAfterSeconds()).isEqualTo(600L);
            });
            // 잠긴 동안은 맞는 코드도 거절
            assertThatThrownBy(() -> otp(operator, codeNow(secret), false)).isInstanceOf(MfaLockedException.class);
        }

        @Test
        void 잠금_키는_sha256_mfa_운영자ID_이고_이메일_로그인_키와_다르다() {
            settings(true, 14, 1, 3, clock.instant().minus(Duration.ofDays(1)));
            long operator = newOperator();
            String secret = enrollDirect(operator);
            assertThatThrownBy(() -> otp(operator, wrongCode(secret), false)).isInstanceOf(MfaCodeInvalidException.class);

            String key = Digests.sha256Hex("mfa:" + operator);
            assertThat(MfaAttemptService.key(operator)).isEqualTo(key);
            assertThat(jdbc.queryForObject("SELECT fail_count FROM login_attempts WHERE email_key = ?", Integer.class, key)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM login_attempts WHERE email_key = ?", Integer.class,
                    Digests.sha256Hex("admin:" + email(operator)))).isZero();
        }

        @Test
        void 잠금이_끝나면_다시_시도할_수_있고_성공하면_횟수를_지운다() {
            settings(true, 14, 1, 3, clock.instant().minus(Duration.ofDays(1)));
            long operator = newOperator();
            String secret = enrollDirect(operator);
            for (int i = 0; i < 3; i++) {
                assertThatThrownBy(() -> otp(operator, wrongCode(secret), false)).isInstanceOf(CommonException.class);
            }
            clock.advance(Duration.ofSeconds(601));

            otp(operator, codeNow(secret), false);

            assertThat(failCount(operator)).isZero();
        }

        @Test
        void 로그인_2단계와_복구_코드_새로_만들기가_같은_키를_쓴다() {
            settings(true, 14, 1, 3, clock.instant().minus(Duration.ofDays(1)));
            long operator = newOperator();
            String secret = enrollDirect(operator);
            assertThatThrownBy(() -> accounts.regenerateRecoveryCodes(actor(operator), wrongCode(secret))).isInstanceOf(MfaCodeInvalidException.class);
            assertThatThrownBy(() -> accounts.regenerateRecoveryCodes(actor(operator), wrongCode(secret))).isInstanceOf(MfaCodeInvalidException.class);

            assertThatThrownBy(() -> otp(operator, wrongCode(secret), false)).isInstanceOf(MfaLockedException.class);
        }
    }

    @Nested
    class 복구_코드 {

        @Test
        void 한_번만_쓸_수_있고_남은_개수를_알리며_감사를_남긴다() {
            mfaOn();
            long operator = newOperator();
            Enrolled enrolled = enrollViaService(operator);
            String code = enrolled.recoveryCodes().get(0);

            MfaVerifyResult result = recovery(operator, code);

            assertThat(result.method()).isEqualTo(MfaLoginMethod.RECOVERY_CODE);
            assertThat(result.recoveryCodesLeft()).isEqualTo(9);
            assertThat(lastAudit(operator, "MFA_RECOVERY_CODE_USED")).contains("\"recoveryCodesLeft\":9");
            assertThat(lastAudit(operator, "LOGIN_SUCCESS")).contains("\"mfa\":\"RECOVERY_CODE\"");
            assertThatThrownBy(() -> recovery(operator, code)).isInstanceOf(RecoveryCodeInvalidException.class);
        }

        @Test
        void 대소문자와_하이픈을_무시한다() {
            mfaOn();
            long operator = newOperator();
            Enrolled enrolled = enrollViaService(operator);

            assertThat(recovery(operator, enrolled.recoveryCodes().get(1).replace("-", "").toLowerCase(Locale.ROOT)).recoveryCodesLeft()).isEqualTo(9);
        }

        @Test
        void 틀리면_RECOVERY_CODE_INVALID_이고_실패_횟수에_센다() {
            mfaOn();
            long operator = newOperator();
            enrollViaService(operator);

            assertThatThrownBy(() -> recovery(operator, "ABCDE-FGHJK")).isInstanceOfSatisfying(RecoveryCodeInvalidException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo("RECOVERY_CODE_INVALID"));
            assertThat(failCount(operator)).isEqualTo(1);
            assertThat(lastAudit(operator, "MFA_FAILURE")).contains("\"reason\":\"RECOVERY_CODE_INVALID\"");
        }

        @Test
        void 다른_운영자의_복구_코드로는_통과하지_않는다() {
            mfaOn();
            long owner = newOperator();
            Enrolled enrolled = enrollViaService(owner);
            long other = newOperator();
            enrollViaService(other);

            assertThatThrownBy(() -> recovery(other, enrolled.recoveryCodes().get(0))).isInstanceOf(RecoveryCodeInvalidException.class);
        }

        @Test
        void DB_에는_해시만_있다() {
            mfaOn();
            long operator = newOperator();
            Enrolled enrolled = enrollViaService(operator);

            String stored = String.join(",", jdbc.queryForList("SELECT code_hash FROM operator_mfa_recovery_codes WHERE operator_id = ?", String.class, operator));
            for (String code : enrolled.recoveryCodes()) {
                assertThat(stored).doesNotContain(code).doesNotContain(code.replace("-", ""));
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_mfa_recovery_codes WHERE operator_id = ?", Integer.class, operator)).isEqualTo(10);
        }
    }

    @Nested
    class 신뢰_기기 {

        @Test
        void 다시_묻지_않기를_고르면_쿠키_값을_주고_DB_에는_해시만_둔다() {
            mfaOn();
            long operator = newOperator();
            String secret = enrollDirect(operator);

            MfaVerifyResult result = otp(operator, codeNow(secret), true);

            assertThat(result.trustToken()).isNotBlank();
            assertThat(result.trustToken().length()).isGreaterThanOrEqualTo(43); // 256비트 base64url
            Map<String, Object> row = jdbc.queryForMap("SELECT token_hash, agent, ip FROM operator_trusted_devices WHERE operator_id = ?", operator);
            assertThat(((String) row.get("token_hash")).trim()).isEqualTo(Digests.sha256Hex(result.trustToken())).doesNotContain(result.trustToken());
            assertThat(row.get("agent")).isEqualTo("Chrome / Windows / Desktop");
            assertThat(devices.isTrusted(operator, result.trustToken())).isTrue();
        }

        @Test
        void 기본_기간은_1개월_30일이고_지나면_통과하지_않는다() {
            mfaOn();
            long operator = newOperator();
            String token = otp(operator, codeNow(enrollDirect(operator)), true).trustToken();

            assertThat(jdbc.queryForObject("SELECT extract(epoch FROM expires_at - trusted_at)::bigint FROM operator_trusted_devices WHERE operator_id = ?", Long.class, operator))
                    .isEqualTo(Duration.ofDays(30).toSeconds());
            clock.advance(Duration.ofDays(29));
            assertThat(devices.isTrusted(operator, token)).isTrue();
            clock.advance(Duration.ofDays(1).plusSeconds(1));
            assertThat(devices.isTrusted(operator, token)).isFalse();
        }

        @Test
        void 기간은_설정_개월_곱하기_30일이다() {
            settings(true, 14, 12, 5, clock.instant().minus(Duration.ofDays(1)));
            long operator = newOperator();
            MfaVerifyResult result = otp(operator, codeNow(enrollDirect(operator)), true);

            assertThat(result.trustDays()).isEqualTo(360);
            assertThat(jdbc.queryForObject("SELECT extract(epoch FROM expires_at - trusted_at)::bigint FROM operator_trusted_devices WHERE operator_id = ?", Long.class, operator))
                    .isEqualTo(Duration.ofDays(360).toSeconds());
        }

        @Test
        void 다른_운영자의_기기_기록으로는_통과하지_않는다() {
            mfaOn();
            long owner = newOperator();
            String token = otp(owner, codeNow(enrollDirect(owner)), true).trustToken();
            long other = newOperator();
            enrollDirect(other);

            assertThat(devices.isTrusted(other, token)).isFalse();
            assertThat(devices.isTrusted(owner, token)).isTrue();
        }
    }

    @Test
    void 대기_중에_OTP_가_초기화되면_MFA_PENDING_EXPIRED() {
        mfaOn();
        long operator = newOperator();
        String secret = enrollDirect(operator);
        unenroll(operator);

        assertThatThrownBy(() -> otp(operator, codeNow(secret), false)).isInstanceOfSatisfying(CommonException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo("MFA_PENDING_EXPIRED"));
    }

    private int failCount(long operatorId) {
        return jdbc.queryForList("SELECT fail_count FROM login_attempts WHERE email_key = ?", Integer.class, MfaAttemptService.key(operatorId)).stream().findFirst().orElse(0);
    }
}
