package com.community.membernotification;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceApiTestBase;
import com.community.membernotification.dto.response.MemberNotificationItemView;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.membernotification.service.MemberNotificationService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 회원 알림 {@code POINTS_EXPIRING}(포인트 소멸 예정 — 알림 배치 {@code point-expiry-notice}, commerce.md 14)이 알림함에서 제목·본문·링크로 그려진다 — 공개 사이트 앱 문맥 전체(실제 PostgreSQL).
 */
class PointsExpiringNotificationTest extends CommerceApiTestBase {

    @Autowired
    private MemberNotificationPublisher publisher;

    @Autowired
    private MemberNotificationService notifications;

    @Test
    void 알림함에는_소멸_예정_합_가장_가까운_소멸일_일수가_보이고_누르면_내_포인트() {
        long member = newMember();
        Integer made = tx.execute(status -> publisher.pointsExpiring(member, 1200, LocalDate.of(2026, 11, 25), 30));
        assertThat(made).isEqualTo(1);

        List<MemberNotificationItemView> items = notifications.list(member, null, 10, null).items();

        assertThat(items).hasSize(1);
        MemberNotificationItemView item = items.get(0);
        assertThat(item.type()).isEqualTo("POINTS_EXPIRING");
        assertThat(item.category()).isEqualTo("system");
        assertThat(item.title()).contains("1,200", "2026-11-25");
        assertThat(item.body()).contains("30");
        assertThat(item.link()).isEqualTo("/me/points");
        assertThat(notifications.list(member, "system", 10, null).items()).as("시스템 거르기에 나온다").hasSize(1);
    }

    @Test
    void 양이_0_이거나_탈퇴한_회원이면_만들지_않는다() {
        long member = newMember();
        Integer none = tx.execute(status -> publisher.pointsExpiring(member, 0, LocalDate.of(2026, 11, 25), 30));
        assertThat(none).isZero();

        long withdrawn = newMember();
        markWithdrawn(withdrawn);
        Integer gone = tx.execute(status -> publisher.pointsExpiring(withdrawn, 500, LocalDate.of(2026, 11, 25), 30));
        assertThat(gone).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id IN (?, ?)", Integer.class, member, withdrawn)).isZero();
    }
}
