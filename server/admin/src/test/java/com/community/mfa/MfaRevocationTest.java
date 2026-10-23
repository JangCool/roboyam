package com.community.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.mfa.dto.response.OperatorMfaView;
import com.community.mfa.exception.MfaResetSelfException;
import com.community.mfa.service.MfaAccountService;
import com.community.mfa.service.MfaAttemptService;
import com.community.mfa.service.OperatorMfaService;
import com.community.mfa.service.TrustedDeviceService;
import com.community.operator.dto.request.OperatorStatusRequest;
import com.community.operator.exception.OperatorNotFoundException;
import com.community.operator.service.OperatorService;
import com.community.permission.exception.PermissionDeniedException;
import com.community.profile.dto.request.PasswordChangeRequest;
import com.community.profile.service.PasswordChangeService;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 신뢰 기기를 모두 끊는 때(비밀번호 변경·재설정 — 재설정은 {@link MfaLoginFlowTest}, 다시 등록 — {@link MfaEnrollmentTest}, 계정 잠김, 루트의 OTP 초기화)와
 * 운영자 상세의 2차 인증(조회·루트의 초기화, 본인은 409) — commerce-foundation-contract 「0-3」, commerce.md 13-3.
 */
class MfaRevocationTest extends MfaSpringTestBase {

    @Autowired
    private TrustedDeviceService devices;
    @Autowired
    private PasswordChangeService passwordChanges;
    @Autowired
    private OperatorService operatorService;
    @Autowired
    private OperatorMfaService operatorMfa;
    @Autowired
    private MfaAccountService accounts;

    @Nested
    class 신뢰_기기_끊기 {

        @Test
        void 비밀번호를_바꾸면_그_운영자의_신뢰_기기를_모두_끊는다() {
            long operator = newOperator();
            devices.trust(operator, 30, IP, null);
            devices.trust(operator, 30, IP, null);
            long other = newOperator();
            String otherToken = devices.trust(other, 30, IP, null);

            passwordChanges.change(actor(operator), new PasswordChangeRequest(PASSWORD, "NewPassw0rd-change"), "current-session-id");

            assertThat(deviceCount(operator)).isZero();
            assertThat(devices.isTrusted(other, otherToken)).isTrue(); // 다른 운영자의 기기는 그대로
            assertThat(lastAudit(operator, "MFA_TRUSTED_DEVICE_REVOKE")).contains("\"cause\":\"PASSWORD_CHANGE\"", "\"count\":2", "\"all\":true");
        }

        @Test
        void 계정을_잠그면_신뢰_기기를_모두_끊는다() {
            long operator = newOperator();
            devices.trust(operator, 30, IP, null);

            operatorService.changeStatus(actor(rootId()), Long.toString(operator), new OperatorStatusRequest(null, true, null, null));

            assertThat(deviceCount(operator)).isZero();
            assertThat(lastAudit(operator, "MFA_TRUSTED_DEVICE_REVOKE")).contains("\"cause\":\"ACCOUNT_LOCKED\"");
        }

        @Test
        void 잠금이_아닌_상태_변경은_끊지_않는다() {
            long operator = newOperator();
            devices.trust(operator, 30, IP, null);

            operatorService.changeStatus(actor(rootId()), Long.toString(operator), new OperatorStatusRequest(null, false, false, true));

            assertThat(deviceCount(operator)).isEqualTo(1);
        }

        @Test
        void 내가_기기_하나를_끊거나_모두_끊을_수_있고_남의_기기는_404() {
            long operator = newOperator();
            String current = devices.trust(operator, 30, IP, null);
            devices.trust(operator, 30, IP, null);
            long other = newOperator();
            devices.trust(other, 30, IP, null);
            String otherId = jdbc.queryForObject("SELECT id::text FROM operator_trusted_devices WHERE operator_id = ?", String.class, other);
            String currentId = jdbc.queryForObject("SELECT id::text FROM operator_trusted_devices WHERE operator_id = ? AND token_hash = ?", String.class, operator,
                    TrustedDeviceService.hash(current));

            assertThat(accounts.devices(actor(operator), current)).hasSize(2).filteredOn(d -> d.current()).hasSize(1);
            assertThatThrownBy(() -> accounts.revokeDevice(actor(operator), otherId, current)).isInstanceOfSatisfying(
                    com.community.mfa.exception.MfaDeviceNotFoundException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
            assertThat(accounts.revokeDevice(actor(operator), currentId, current)).isTrue(); // 지금 브라우저였다
            assertThat(accounts.revokeAllDevices(actor(operator))).isEqualTo(1);
            assertThat(deviceCount(operator)).isZero();
            assertThat(deviceCount(other)).isEqualTo(1);
        }
    }

    @Nested
    class 루트의_OTP_초기화 {

        @Test
        void 루트는_다른_운영자의_OTP_복구_코드_신뢰_기기_실패_기록을_지우고_감사를_남긴다() {
            long operator = newOperator();
            enrollViaService(operator);
            devices.trust(operator, 30, IP, null);
            jdbc.update("INSERT INTO login_attempts (email_key, fail_count, updated_at) VALUES (?, 2, now()) ON CONFLICT (email_key) DO UPDATE SET fail_count = 2",
                    MfaAttemptService.key(operator));
            long root = rootId();

            operatorMfa.reset(actor(root), Long.toString(operator));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_mfa WHERE operator_id = ?", Integer.class, operator)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_mfa_recovery_codes WHERE operator_id = ?", Integer.class, operator)).isZero();
            assertThat(deviceCount(operator)).isZero();
            assertThat(jdbc.queryForObject("SELECT fail_count FROM login_attempts WHERE email_key = ?", Integer.class, MfaAttemptService.key(operator))).isZero();
            assertThat(lastAudit(operator, "MFA_RESET")).contains("\"via\":\"ADMIN\"", "\"wasEnrolled\":true", "\"recoveryCodes\":10", "\"trustedDevices\":1");
            assertThat(jdbc.queryForObject("SELECT actor_id FROM audit_logs WHERE action = 'MFA_RESET' AND target_id = ? ORDER BY id DESC LIMIT 1", Long.class,
                    Long.toString(operator))).isEqualTo(root);
        }

        @Test
        void 루트_본인은_409_MFA_RESET_SELF() {
            long root = rootId();

            assertThatThrownBy(() -> operatorMfa.reset(actor(root), Long.toString(root))).isInstanceOfSatisfying(MfaResetSelfException.class,
                    e -> assertThat(e.getStatus().value()).isEqualTo(409));
        }

        @Test
        void 루트가_아니면_403_이고_거부_감사를_남긴다() {
            long operator = newOperator();
            long target = newOperator();
            enrollDirect(target);

            assertThatThrownBy(() -> operatorMfa.reset(actor(operator), Long.toString(target))).isInstanceOf(PermissionDeniedException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM operator_mfa WHERE operator_id = ?", Integer.class, target)).isOne();
            assertThat(jdbc.queryForObject("SELECT detail FROM audit_logs WHERE actor_id = ? AND action = 'PERMISSION_DENIED' ORDER BY id DESC LIMIT 1", String.class,
                    operator)).contains("SUPER_ONLY");
        }

        @Test
        void 없는_운영자는_404() {
            assertThatThrownBy(() -> operatorMfa.reset(actor(rootId()), "1")).isInstanceOf(OperatorNotFoundException.class);
            assertThatThrownBy(() -> operatorMfa.view("not-a-number")).isInstanceOf(OperatorNotFoundException.class);
        }
    }

    @Nested
    class 운영자_상세_조회 {

        @Test
        void 사이트가_꺼져_있으면_사용_안_함() {
            mfaOff();
            OperatorMfaView view = operatorMfa.view(Long.toString(newOperator()));

            assertThat(view.siteEnabled()).isFalse();
            assertThat(view.graceEndsAt()).isNull();
            assertThat(view.graceExpired()).isFalse();
        }

        @Test
        void 등록됨과_신뢰_기기_수_기간이_끝난_기기는_세지_않는다() {
            mfaOn();
            long operator = newOperator();
            enrollDirect(operator);
            devices.trust(operator, 30, IP, null);
            devices.trust(operator, 1, IP, null);
            clock.advance(Duration.ofDays(2));

            OperatorMfaView view = operatorMfa.view(Long.toString(operator));

            assertThat(view.enrolled()).isTrue();
            assertThat(view.enrolledAt()).isNotNull();
            assertThat(view.trustedDevices()).isEqualTo(1);
        }

        @Test
        void 미등록은_유예_마감과_지남_여부() {
            settings(true, 2, 1, 5, clock.instant().minus(Duration.ofDays(5)));
            OperatorMfaView expired = operatorMfa.view(Long.toString(newOperator(clock.instant().minus(Duration.ofDays(50)))));
            OperatorMfaView inGrace = operatorMfa.view(Long.toString(newOperator(clock.instant())));

            assertThat(expired.enrolled()).isFalse();
            assertThat(expired.graceExpired()).isTrue();
            assertThat(inGrace.graceExpired()).isFalse();
            assertThat(inGrace.graceEndsAt()).isAfter(clock.instant());
        }
    }
}
