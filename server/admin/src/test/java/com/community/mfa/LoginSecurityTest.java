package com.community.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.mfa.dto.request.LoginSecuritySaveRequest;
import com.community.mfa.dto.response.LoginSecurityPendingOperator;
import com.community.mfa.dto.response.LoginSecurityView;
import com.community.mfa.exception.MfaRootNotEnrolledException;
import com.community.mfa.service.LoginSecurityService;
import com.community.permission.exception.PermissionDeniedException;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 로그인 보안 설정 — 루트만 바꾼다, 루트 미등록이면 켜기 409, 켜면 enabled_at 갱신, 바꾼 칸만 감사, 끄면 운영자 알림(심각도 높음)·켜면 등록 안내, 범위 검사, 등록 현황
 * (commerce-foundation-contract 「0-3」 설정, commerce.md 13-3).
 */
class LoginSecurityTest extends MfaSpringTestBase {

    @Autowired
    private LoginSecurityService service;

    private LoginSecurityView save(long actorId, boolean enabled, int grace, int trust, int failures) {
        return service.save(actor(actorId), new LoginSecuritySaveRequest(enabled, grace, trust, failures));
    }

    private int notifications(long operatorId, String event) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM admin_notifications WHERE operator_id = ? AND event_code = ?", Integer.class, operatorId, event);
        return count == null ? 0 : count;
    }

    private String lastSettingsAudit() {
        return jdbc.queryForObject("SELECT detail FROM audit_logs WHERE action = 'MFA_SETTINGS_UPDATE' AND target_type = 'LOGIN_SECURITY' ORDER BY id DESC LIMIT 1", String.class);
    }

    private int settingsAudits() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'MFA_SETTINGS_UPDATE'", Integer.class);
        return count == null ? 0 : count;
    }

    @Nested
    class 루트만 {

        @Test
        void 루트가_아니면_읽기도_저장도_403_이고_거부_감사를_남긴다() {
            long operator = newOperator();

            assertThatThrownBy(() -> service.view(actor(operator))).isInstanceOf(PermissionDeniedException.class);
            assertThatThrownBy(() -> save(operator, false, 14, 1, 5)).isInstanceOfSatisfying(PermissionDeniedException.class,
                    e -> assertThat(e.getStatus().value()).isEqualTo(403));
            List<Map<String, Object>> denied = jdbc.queryForList("SELECT result, target_type, detail FROM audit_logs WHERE actor_id = ? AND action = 'PERMISSION_DENIED'",
                    operator);
            assertThat(denied).hasSize(2).allSatisfy(row -> {
                assertThat(row.get("result")).isEqualTo("DENIED");
                assertThat(row.get("target_type")).isEqualTo("LOGIN_SECURITY");
                assertThat((String) row.get("detail")).contains("\"reason\":\"SUPER_ONLY\"");
            });
        }

        @Test
        void 루트는_읽을_수_있다() {
            mfaOff();
            LoginSecurityView view = service.view(actor(rootId()));

            assertThat(view.enabled()).isFalse();
            assertThat(view.graceDays()).isEqualTo(14);
            assertThat(view.trustMonths()).isEqualTo(1);
            assertThat(view.maxFailures()).isEqualTo(5);
        }
    }

    @Nested
    class 켜기 {

        @Test
        void 루트가_OTP_를_등록하지_않았으면_켜기는_409_이고_바뀌지_않는다() {
            mfaOff();
            long root = rootId();
            unenroll(root);

            assertThatThrownBy(() -> save(root, true, 14, 1, 5)).isInstanceOfSatisfying(MfaRootNotEnrolledException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo("MFA_ROOT_NOT_ENROLLED");
                assertThat(e.getStatus().value()).isEqualTo(409);
            });
            assertThat(jdbc.queryForObject("SELECT mfa_enabled FROM admin_security_settings WHERE id = 1", Boolean.class)).isFalse();
            assertThat(service.view(actor(root)).rootEnrolled()).isFalse();
        }

        @Test
        void 켜면_켠_시각과_켠_사람이_남고_유예가_그때부터_시작한다() {
            mfaOff();
            long root = rootId();
            enrollDirect(root);
            Instant now = clock.instant();

            LoginSecurityView view = save(root, true, 14, 1, 5);

            OffsetDateTime enabledAt = jdbc.queryForObject("SELECT mfa_enabled_at FROM admin_security_settings WHERE id = 1", OffsetDateTime.class);
            Long enabledBy = jdbc.queryForObject("SELECT mfa_enabled_by FROM admin_security_settings WHERE id = 1", Long.class);
            assertThat(enabledAt.toInstant().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(now.truncatedTo(ChronoUnit.MILLIS));
            assertThat(enabledBy).isEqualTo(root);
            assertThat(view.enabled()).isTrue();
            assertThat(view.rootEnrolled()).isTrue();
            assertThat(view.enabledBy()).isEqualTo(jdbc.queryForObject("SELECT operator_name FROM operators WHERE id = ?", String.class, root));
        }

        @Test
        void 켜면_운영자_전원에게_등록_안내를_보내고_켠_사람은_뺀다() {
            mfaOff();
            long root = rootId();
            enrollDirect(root);
            long operator = newOperator();
            int rootBefore = notifications(root, "MFA_ENABLED");

            save(root, true, 14, 1, 5);

            assertThat(notifications(operator, "MFA_ENABLED")).isEqualTo(1);
            assertThat(notifications(root, "MFA_ENABLED")).isEqualTo(rootBefore);
            assertThat(jdbc.queryForObject("SELECT link_path FROM admin_notifications WHERE operator_id = ? AND event_code = 'MFA_ENABLED'", String.class, operator))
                    .isEqualTo("/me?tab=security");
        }

        @Test
        void 이미_켜진_채로_루트가_미등록이어도_다른_칸은_저장된다() {
            long root = rootId();
            settings(true, 14, 1, 5, clock.instant().minus(Duration.ofDays(1)));
            unenroll(root);

            LoginSecurityView view = save(root, true, 20, 1, 5);

            assertThat(view.graceDays()).isEqualTo(20);
        }
    }

    @Nested
    class 끄기 {

        @Test
        void 끄면_감사_전후와_운영자_전원에게_심각도_높음_알림_끈_사람_포함() {
            long root = rootId();
            enrollDirect(root);
            settings(true, 14, 1, 5, clock.instant().minus(Duration.ofDays(1)));
            long operator = newOperator();
            long locked = newOperator();
            jdbc.update("UPDATE operators SET locked = true WHERE id = ?", locked);
            int rootBefore = notifications(root, "MFA_DISABLED");

            save(root, false, 14, 1, 5);

            assertThat(lastSettingsAudit()).contains("\"before\":{\"enabled\":true}", "\"after\":{\"enabled\":false}");
            assertThat(notifications(operator, "MFA_DISABLED")).isEqualTo(1);
            assertThat(notifications(root, "MFA_DISABLED")).isEqualTo(rootBefore + 1);
            assertThat(notifications(locked, "MFA_DISABLED")).isZero(); // 활성 운영자만
            assertThat(jdbc.queryForObject("SELECT severity FROM admin_notifications WHERE operator_id = ? AND event_code = 'MFA_DISABLED'", String.class, operator))
                    .isEqualTo("CRITICAL");
        }
    }

    @Nested
    class 감사 {

        @Test
        void 바꾼_칸만_전후로_남긴다() {
            mfaOff();
            long root = rootId();

            save(root, false, 20, 1, 5);

            String detail = lastSettingsAudit();
            assertThat(detail).contains("\"before\":{\"graceDays\":14}", "\"after\":{\"graceDays\":20}").doesNotContain("enabled", "trustMonths", "maxFailures");
        }

        @Test
        void 바뀐_것이_없으면_감사도_알림도_없다() {
            mfaOff();
            long root = rootId();
            int before = settingsAudits();

            save(root, false, 14, 1, 5);

            assertThat(settingsAudits()).isEqualTo(before);
        }
    }

    @Test
    void 범위를_벗어나면_400_VALIDATION_칸_차례대로() {
        long root = rootId();

        assertThatThrownBy(() -> service.save(actor(root), new LoginSecuritySaveRequest(null, 0, 13, 2))).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.getFields().keySet()).containsExactly("enabled", "graceDays", "trustMonths", "maxFailures"));
        assertThatThrownBy(() -> save(root, false, 91, 0, 11)).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.getFields().keySet()).containsExactly("graceDays", "trustMonths", "maxFailures"));
        save(root, false, 90, 12, 10); // 경계는 된다
        save(root, false, 1, 1, 3);
    }

    @Test
    void 등록_현황은_활성_운영자_기준이고_켜져_있을_때만_유예를_센다() {
        long root = rootId();
        enrollDirect(root);
        settings(true, 2, 1, 5, clock.instant().minus(Duration.ofDays(5)));
        long expired = newOperator(clock.instant().minus(Duration.ofDays(100)));
        long inGrace = newOperator(clock.instant());
        long enrolled = newOperator();

        LoginSecurityView before = service.view(actor(root));
        enrollDirect(enrolled);
        LoginSecurityView afterEnroll = service.view(actor(root));
        jdbc.update("UPDATE operators SET locked = true WHERE id = ?", expired);
        LoginSecurityView afterLock = service.view(actor(root));

        Integer active = jdbc.queryForObject("SELECT count(*) FROM operators o WHERE o.approved AND NOT o.locked AND NOT o.expired AND o.enabled "
                + "AND (o.account_start_at IS NULL OR o.account_start_at <= now()) AND (o.account_end_at IS NULL OR o.account_end_at >= now())", Integer.class);
        assertThat(afterLock.summary().operators()).isEqualTo(active);
        assertThat(afterEnroll.summary().enrolled()).isEqualTo(before.summary().enrolled() + 1);
        assertThat(afterLock.summary().graceExpired()).isEqualTo(afterEnroll.summary().graceExpired() - 1);
        assertThat(before.summary().inGrace()).isPositive();
        List<LoginSecurityPendingOperator> pending = afterLock.summary().pending();
        assertThat(pending).hasSizeLessThanOrEqualTo(LoginSecurityService.PENDING_LIMIT);
        assertThat(pending.stream().map(LoginSecurityPendingOperator::graceEndsAt).filter(Objects::nonNull).toList())
                .isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(inGrace).isPositive();

        mfaOff();
        LoginSecurityView off = service.view(actor(root));
        assertThat(off.summary().inGrace()).isZero();
        assertThat(off.summary().graceExpired()).isZero();
        assertThat(off.summary().pending()).allSatisfy(p -> assertThat(p.graceEndsAt()).isNull());
    }
}
