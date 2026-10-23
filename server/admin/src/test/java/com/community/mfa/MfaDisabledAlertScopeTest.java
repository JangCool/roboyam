package com.community.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.mfa.dto.request.LoginSecuritySaveRequest;
import com.community.mfa.service.LoginSecurityService;
import com.community.notification.dto.request.NotificationEventUpdateRequest;
import com.community.notification.dto.response.NotificationEventView;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationEventAdminService;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 「2차 인증 꺼짐」 알림은 받는 범위를 「팀에만」으로 줄일 수 없다 — 누가 몰래 끈 것을 모든 운영자가 바로 알아채게 하는 보안 경보라서다
 * (commerce-foundation-contract 「0-3」, 알림 「팀에만 보내기」와의 세션 정리 결정). 「2차 인증 켜짐」은 막지 않는다.
 */
class MfaDisabledAlertScopeTest extends MfaSpringTestBase {

    @Autowired
    private NotificationEventAdminService events;
    @Autowired
    private LoginSecurityService loginSecurity;

    private static NotificationEventUpdateRequest teamsOnly() {
        return new NotificationEventUpdateRequest(true, true, null, null, false);
    }

    private int notifications(long operatorId, String event) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM admin_notifications WHERE operator_id = ? AND event_code = ?", Integer.class, operatorId, event);
        return count == null ? 0 : count;
    }

    @Test
    void 꺼짐_알림을_팀에만으로_바꾸면_400_이고_이유_문구가_칸에_온다() {
        long root = rootId();

        assertThatThrownBy(() -> events.update(actor(root), "MFA_DISABLED", teamsOnly())).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.getFields().keySet()).containsExactly("defaultRecipients");
            assertThat(e.getFields().get("defaultRecipients").key()).isEqualTo(AdminNotificationEvent.MFA_DISABLED.alwaysEveryoneReasonKey());
        });
        assertThat(jdbc.queryForObject("SELECT default_recipients FROM notification_events WHERE code = 'MFA_DISABLED'", Boolean.class)).isTrue();
    }

    @Test
    void 켜짐_알림은_막지_않는다_팀이_없을_때의_일반_검사까지_간다() {
        long root = rootId();

        // 이유 문구가 아니라 「팀을 고르세요」(teamIds) — alwaysEveryone 검사에 걸리지 않았다
        assertThatThrownBy(() -> events.update(actor(root), "MFA_ENABLED", teamsOnly())).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.getFields().keySet()).containsExactly("teamIds"));
    }

    @Test
    void 화면에_주는_한_줄에_이유가_있고_받는_범위는_늘_권한자_전원이다() {
        NotificationEventView disabled = events.one("MFA_DISABLED");
        NotificationEventView enabled = events.one("MFA_ENABLED");

        assertThat(disabled.teamsOnlyBlockedReason()).isNotBlank().doesNotContain("notification.event.");
        assertThat(disabled.defaultRecipients()).isTrue();
        assertThat(disabled.broadcastAllowed()).isTrue(); // 팀·공유 채널을 더하는 것은 된다
        assertThat(enabled.teamsOnlyBlockedReason()).isNull();
    }

    @Test
    void DB_값이_팀에만이어도_꺼짐_알림은_운영자_전원에게_간다() {
        long root = rootId();
        enrollDirect(root);
        settings(true, 14, 1, 5, clock.instant().minus(Duration.ofDays(1)));
        long operator = newOperator();
        jdbc.update("UPDATE notification_events SET default_recipients = false WHERE code = 'MFA_DISABLED'");
        try {
            assertThat(events.one("MFA_DISABLED").defaultRecipients()).isTrue();

            loginSecurity.save(actor(root), new LoginSecuritySaveRequest(false, 14, 1, 5));

            assertThat(notifications(operator, "MFA_DISABLED")).isEqualTo(1);
        } finally {
            jdbc.update("UPDATE notification_events SET default_recipients = true WHERE code = 'MFA_DISABLED'");
        }
    }
}
