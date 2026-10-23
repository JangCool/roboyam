package com.community.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditActions;
import com.community.batch.catalog.BatchCatalog;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.NotificationArea;
import com.community.notification.entity.NotificationRecipientRule;
import com.community.notification.entity.NotificationSeverity;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * 감사 행위·알림 이벤트·배치·오류 코드의 카탈로그와 문구 번들(ko·en) — commerce-foundation-contract 「0-3」 감사 목록, 끄기 = 심각도 높음 알림, 정리 배치.
 */
class MfaCatalogTest {

    private static final List<String> ACTIONS = List.of("MFA_ENROLL", "MFA_REENROLL", "MFA_RECOVERY_CODES_REGENERATE", "MFA_FAILURE", "MFA_RECOVERY_CODE_USED",
            "MFA_TRUSTED_DEVICE_REVOKE", "MFA_RESET", "MFA_SETTINGS_UPDATE");
    private static final List<String> ERRORS = List.of("MFA_CODE_INVALID", "MFA_CODE_REUSED", "RECOVERY_CODE_INVALID", "MFA_LOCKED", "MFA_PENDING_EXPIRED",
            "MFA_ALREADY_ENROLLED", "MFA_ENROLLMENT_NOT_STARTED", "MFA_NOT_ENROLLED", "MFA_DEVICE_NOT_FOUND", "MFA_ROOT_NOT_ENROLLED", "MFA_RESET_SELF");

    private static Properties bundle(String language) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MfaCatalogTest.class.getResourceAsStream("/messages/messages_" + language + ".properties")) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    void 계약의_감사_행위가_모두_있고_두_언어에_이름이_있다() throws IOException {
        assertThat(AuditActions.all()).containsAll(ACTIONS);
        for (String language : List.of("ko", "en")) {
            Properties messages = bundle(language);
            for (String action : ACTIONS) {
                assertThat(messages.getProperty("audit.action." + action)).as(language + " " + action).isNotBlank();
            }
            assertThat(messages.getProperty("audit.targetType.LOGIN_SECURITY")).isNotBlank();
            for (String reason : List.of("CODE_INVALID", "CODE_REUSED", "RECOVERY_CODE_INVALID")) {
                assertThat(messages.getProperty("audit.reason." + reason)).as(language + " " + reason).isNotBlank();
            }
        }
    }

    @Test
    void 오류_코드의_문구가_두_언어에_있다() throws IOException {
        for (String language : List.of("ko", "en")) {
            Properties messages = bundle(language);
            for (String code : ERRORS) {
                assertThat(messages.getProperty("error." + code)).as(language + " " + code).isNotBlank();
            }
            assertThat(messages.getProperty("error.MFA_CODE_INVALID")).contains("{0}"); // 남은 횟수
        }
    }

    @Test
    void 끄기는_보안_심각도_높음_운영자_전원_켜기는_운영자_전원_등록_안내() throws IOException {
        AdminNotificationEvent disabled = AdminNotificationEvent.MFA_DISABLED;
        AdminNotificationEvent enabled = AdminNotificationEvent.MFA_ENABLED;

        assertThat(disabled.area()).isEqualTo(NotificationArea.SECURITY);
        assertThat(disabled.severity()).isEqualTo(NotificationSeverity.CRITICAL);
        assertThat(disabled.recipient()).isEqualTo(NotificationRecipientRule.ALL_OPERATORS);
        assertThat(disabled.excludeActor()).isFalse();
        assertThat(disabled.requiredPermission()).isNull();
        assertThat(disabled.featureReady()).isTrue();
        assertThat(enabled.recipient()).isEqualTo(NotificationRecipientRule.ALL_OPERATORS);
        assertThat(enabled.excludeActor()).isTrue();
        assertThat(enabled.linkPath()).isEqualTo("/me?tab=security");
        List<String> keys = new ArrayList<>(disabled.bundleKeys());
        keys.addAll(enabled.bundleKeys());
        for (String language : List.of("ko", "en")) {
            Properties messages = bundle(language);
            for (String key : keys) {
                assertThat(messages.getProperty(key)).as(language + " " + key).isNotNull();
            }
        }
    }

    @Test
    void 정리_배치가_카탈로그와_번들에_있다() throws IOException {
        BatchCatalog batch = BatchCatalog.ofKey("admin-mfa-cleanup");

        assertThat(batch).isNotNull();
        assertThat(batch.procedureName()).isEqualTo("batch_admin_mfa_cleanup");
        for (String language : List.of("ko", "en")) {
            Properties messages = bundle(language);
            for (String key : List.of("batch.job.admin-mfa-cleanup.name", "batch.job.admin-mfa-cleanup.desc", "batch.result.adminMfaCleanup",
                    "batch.result.adminMfaCleanup.none")) {
                assertThat(messages.getProperty(key)).as(language + " " + key).isNotBlank();
            }
        }
    }
}
