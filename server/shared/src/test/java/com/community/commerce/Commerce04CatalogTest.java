package com.community.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.audit.dto.AuditActions;
import com.community.batch.core.BatchCatalog;
import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.entity.MemberEventLockReason;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.notificationtemplate.catalog.MessageTargetCatalog;
import com.community.notificationtemplate.catalog.MessageTargetType;
import com.community.restriction.entity.RestrictionAutoRule;
import com.community.restriction.entity.RestrictionType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * 커머스 0-4 가 더한 카탈로그 줄(오류 코드·감사 행위·배치·회원 이벤트·알림 문구 변수)이 있고 <b>두 언어</b>에 문구가 있다(exception.md · notification.md · db-batch.md).
 */
class Commerce04CatalogTest {

    private static final List<String> ERROR_CODES = List.of("IDENTITY_ALREADY_VERIFIED", "IDENTITY_NOT_CONFIGURED", "IDENTITY_VERIFICATION_NOT_FOUND",
            "IDENTITY_VERIFICATION_EXPIRED", "IDENTITY_ALREADY_LINKED", "IDENTITY_REVERIFY_BLOCKED", "IDENTITY_PROVIDER_FAILED", "IDENTITY_REQUIRED", "IDENTITY_UNDERAGE",
            "MEMBER_RESTRICTED", "MEMBER_WITHDRAWN", "MEMBER_NOT_FOUND", "RESTRICTION_NOT_FOUND", "RESTRICTION_NOT_ACTIVE", "WITHDRAWN_CONTACT_NOT_FOUND",
            "PORTONE_FIELD_NOT_FOUND");

    private static Properties bundle(String language) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Commerce04CatalogTest.class.getResourceAsStream("/messages/messages_" + language + ".properties")) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static List<String> requiredKeys() {
        List<String> keys = new ArrayList<>();
        ERROR_CODES.forEach(code -> keys.add("error." + code));
        for (String action : List.of(AuditActions.MEMBER_RESTRICTION_GRANT, AuditActions.MEMBER_RESTRICTION_LIFT, AuditActions.WITHDRAWN_CONTACT_REVEAL,
                AuditActions.PORTONE_SETTINGS_UPDATE, AuditActions.PORTONE_SETTINGS_DELETE)) {
            keys.add("audit.action." + action);
        }
        keys.add("audit.targetType.PORTONE_SETTINGS");
        keys.add(BatchCatalog.MEMBER_PRIVACY_RETENTION.nameKey());
        keys.add(BatchCatalog.MEMBER_PRIVACY_RETENTION.descriptionKey());
        keys.addAll(List.of("batch.result.memberPrivacyRetention", "batch.result.memberPrivacyRetention.none"));
        keys.addAll(MemberEvent.FEATURE_RESTRICTED.bundleKeys());
        keys.add("member.notification.FEATURE_RESTRICTED.title");
        for (String variable : List.of("restrictionFeature", "restrictionUntil", "restrictionReason")) {
            keys.add("notificationTemplate.var." + variable + ".desc");
            keys.add("notificationTemplate.var." + variable + ".sample");
        }
        for (RestrictionType type : RestrictionType.values()) {
            keys.add("restriction.feature." + type.name());
        }
        keys.addAll(List.of("restriction.autoRule." + RestrictionAutoRule.GROUP_BUY_HOLD_EXPIRED, "restriction.autoRule." + RestrictionAutoRule.GROUP_BUY_HOLD_EXPIRED + ".reason",
                "restriction.until.at", "restriction.until.none", "restriction.type.required", "restriction.until.past", "restriction.reason.length",
                "restriction.memo.max", "identity.fake.birthDate.required", "identity.fake.birthDate.invalid", "identity.fake.personKey.required",
                "identity.fake.personKey.max", "withdrawnContact.reason.length", "portone.field.required", "portone.field.max"));
        return keys;
    }

    @Test
    void 새_문구는_두_언어에_모두_있다() throws IOException {
        for (String language : List.of("ko", "en")) {
            Properties messages = bundle(language);
            for (String key : requiredKeys()) {
                assertThat(messages.getProperty(key)).as(language + " " + key).isNotBlank();
            }
        }
    }

    @Test
    void 새_감사_행위가_목록에_있다() {
        assertThat(AuditActions.all()).contains("MEMBER_RESTRICTION_GRANT", "MEMBER_RESTRICTION_LIFT", "WITHDRAWN_CONTACT_REVEAL", "PORTONE_SETTINGS_UPDATE",
                "PORTONE_SETTINGS_DELETE");
        assertThat(AuditActions.all()).allMatch(action -> action.length() <= 40); // audit_logs.action
    }

    @Test
    void 기능_제한_알림은_실제_이벤트이고_회원이_끌_수_없는_계정_알림이다() {
        assertThat(MemberNotificationType.FEATURE_RESTRICTED.category()).isEqualTo("system");
        assertThat(MemberEvent.FEATURE_RESTRICTED.featureReady()).isTrue();
        assertThat(MemberEvent.FEATURE_RESTRICTED.lockReason()).isEqualTo(MemberEventLockReason.ACCOUNT);
        assertThat(MemberEvent.of(MemberNotificationType.FEATURE_RESTRICTED)).isEqualTo(MemberEvent.FEATURE_RESTRICTED);
        assertThat(MessageTargetCatalog.find(MessageTargetType.EVENT, "FEATURE_RESTRICTED")).get().satisfies(target -> {
            assertThat(target.inApp().titleKey()).isEqualTo("member.notification.FEATURE_RESTRICTED.title");
            assertThat(target.inApp().argNames()).containsExactly("restriction", "until");
        });
    }
}
