package com.community.point;

import static com.community.point.PointBatchTestSupport.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.batch.core.BatchRunner.RunReport;
import com.community.batch.entity.BatchRunStatus;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.point.batch.PointExpiryNoticeJob;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 포인트 소멸 예정 알림 배치 {@code point-expiry-notice}(commerce.md 14, 계약 「1-1」) — 실제 PostgreSQL 위에서 배치 core 와 실제 회원 알림 발행기로 돌린다.
 * 대상은 파라미터 {@code userId} 로 시험 회원에 묶는다. 묶음의 소멸일은 시계를 되돌려 적립해 만든다(적립 시각 + 365일).
 */
class PointExpiryNoticeJobTest extends PointTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static PointBatchTestSupport batch;
    private static PointExpiryNoticeJob job;

    @BeforeAll
    static void setUpBatch() {
        batch = new PointBatchTestSupport(kit);
        job = new PointExpiryNoticeJob(batch.queries, kit.policy, batch.publisher, batch.clocks);
    }

    /** 지금부터 {@code days} 일 뒤에 소멸하는 무상 묶음(그만큼 전에 적립) — 묶음 ID. 시계는 지금으로 되돌린다 */
    private static long lotExpiringIn(long userId, Instant now, Duration in, int amount) {
        kit.clock.set(now.minus(Duration.ofDays(365)).plus(in));
        long lot = kit.lotOf(kit.grantLine(userId, amount));
        kit.clock.set(now);
        return lot;
    }

    private static List<Map<String, Object>> notices(long userId) {
        return kit.jdbc.queryForList("SELECT type, payload::text AS payload FROM member_notifications WHERE user_id = ? ORDER BY id", userId);
    }

    private static Instant noticedAt(long lotId) {
        Timestamp at = kit.jdbc.queryForObject("SELECT expiry_noticed_at FROM point_lots WHERE id = ?", Timestamp.class, lotId);
        return at == null ? null : at.toInstant();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> notice) {
        return JSON.readValue((String) notice.get("payload"), Map.class);
    }

    @Test
    void 기간_안_묶음이_있는_회원에게_한_건_가장_가까운_소멸일과_기간_안_합을_알리고_두_번_알리지_않는다() {
        long m = kit.newMember();
        Instant now = kit.clock.instant();
        long soon = lotExpiringIn(m, now, Duration.ofDays(10), 100);
        long later = lotExpiringIn(m, now, Duration.ofDays(25), 40);
        long outside = lotExpiringIn(m, now, Duration.ofDays(31), 70); // 알림 기간(30일) 밖
        long paid = kit.chargePaid(m, 500); // 유상은 소멸이 없다
        Instant soonExpires = kit.jdbc.queryForObject("SELECT expires_at FROM point_lots WHERE id = ?", Timestamp.class, soon).toInstant();

        RunReport first = batch.runFor(job, m);

        assertThat(first.status()).isEqualTo(BatchRunStatus.OK);
        assertThat(number(batch.step(first.runId()).get("processed_count"))).as("회원 한 명").isEqualTo(1);
        List<Map<String, Object>> made = notices(m);
        assertThat(made).as("회원당 한 건").hasSize(1);
        assertThat(made.get(0).get("type")).isEqualTo(MemberNotificationType.POINTS_EXPIRING.name());
        Map<String, Object> payload = payload(made.get(0));
        assertThat(number(payload.get("amount"))).as("기간 안 묶음 합").isEqualTo(140);
        assertThat(payload.get("date")).as("가장 가까운 소멸일(한국 날짜)").isEqualTo(soonExpires.atZone(ServiceTime.ZONE).toLocalDate().toString());
        assertThat(number(payload.get("days"))).isEqualTo(30);
        assertThat(noticedAt(soon)).isNotNull();
        assertThat(noticedAt(later)).isNotNull();
        assertThat(noticedAt(outside)).as("기간 밖 묶음은 표시하지 않는다").isNull();
        assertThat(noticedAt(paid)).isNull();
        assertThat(batch.run(first.runId()).get("message_key")).isEqualTo("batch.result.pointExpiryNotice");

        RunReport again = batch.runFor(job, m);

        assertThat(again.status()).as("같은 묶음으로 두 번 알리지 않는다").isEqualTo(BatchRunStatus.SKIP);
        assertThat(notices(m)).hasSize(1);
        assertThat(batch.run(again.runId()).get("message_key")).isEqualTo("batch.result.pointExpiryNotice.none");

        // 이틀 뒤 기간에 새 묶음이 들어와도 회원당 최소 간격(기본 7일) 안이라 쉰다 — 새 묶음은 표시하지 않고 남긴다
        kit.clock.set(now.plus(Duration.ofDays(2)));
        assertThat(batch.runFor(job, m).status()).isEqualTo(BatchRunStatus.SKIP);
        assertThat(notices(m)).hasSize(1);
        assertThat(noticedAt(outside)).as("쉬는 동안 들어온 묶음은 다음 알림에 담는다").isNull();

        // 7일 뒤(한국 날짜로 7일) 다시 — 합은 기간 안 전부(이미 알린 묶음 포함)
        kit.clock.set(now.plus(Duration.ofDays(7)));
        RunReport next = batch.runFor(job, m);

        assertThat(next.status()).isEqualTo(BatchRunStatus.OK);
        List<Map<String, Object>> after = notices(m);
        assertThat(after).hasSize(2);
        assertThat(number(payload(after.get(1)).get("amount"))).isEqualTo(210);
        assertThat(noticedAt(outside)).isNotNull();
        kit.clock.set(now);
        kit.assertInvariants(m);
    }

    @Test
    void 회원당_최소_간격은_한국_날짜로_센다_6일째는_쉬고_7일째에_다시_알린다() {
        long m = kit.newMember();
        Instant now = kit.clock.instant();
        lotExpiringIn(m, now, Duration.ofDays(20), 10);
        assertThat(batch.runFor(job, m).status()).isEqualTo(BatchRunStatus.OK);
        Instant sentAt = kit.jdbc.queryForObject("SELECT expiry_notice_sent_at FROM point_balances WHERE user_id = ?", Timestamp.class, m).toInstant();
        assertThat(sentAt).as("마지막 알림 시각을 잔액 행에 남긴다").isEqualTo(now.truncatedTo(java.time.temporal.ChronoUnit.MICROS));

        // 첫 알림 날의 한국 날짜 23:59 에 받은 것으로 옮겨 「며칠째」를 날짜로만 세는지 본다
        Instant lateThatDay = now.atZone(ServiceTime.ZONE).toLocalDate().plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant().minusSeconds(60);
        kit.jdbc.update("UPDATE point_balances SET expiry_notice_sent_at = ? WHERE user_id = ?", Timestamp.from(lateThatDay), m);
        Instant day6 = now.atZone(ServiceTime.ZONE).toLocalDate().plusDays(6).atStartOfDay(ServiceTime.ZONE).toInstant().plusSeconds(3600);
        Instant day7 = day6.plus(Duration.ofDays(1));
        try {
            kit.clock.set(day6);
            lotExpiringIn(m, day6, Duration.ofDays(15), 5);
            assertThat(batch.runFor(job, m).status()).as("6일째는 쉰다").isEqualTo(BatchRunStatus.SKIP);
            kit.clock.set(day7);
            assertThat(batch.runFor(job, m).status()).as("7일째에는 실행 시각이 앞이어도 다시 알린다").isEqualTo(BatchRunStatus.OK);
            assertThat(notices(m)).hasSize(2);
        } finally {
            kit.clock.set(now);
        }
        kit.assertInvariants(m);
    }

    @Test
    void 간격이_0_이면_새_묶음이_들어올_때마다_알린다() {
        long m = kit.newMember();
        Instant now = kit.clock.instant();
        lotExpiringIn(m, now, Duration.ofDays(10), 10);
        kit.withSetting("expiry_notice_interval_days", 0, () -> {
            batch.runFor(job, m);
            Instant next = now.plus(Duration.ofDays(1));
            try {
                kit.clock.set(next);
                lotExpiringIn(m, next, Duration.ofDays(12), 20);
                assertThat(batch.runFor(job, m).status()).isEqualTo(BatchRunStatus.OK);
            } finally {
                kit.clock.set(now);
            }
        });
        assertThat(notices(m)).hasSize(2);
    }

    @Test
    void 간격이_알림_기간보다_길면_알림_일수_빼기_1_로_줄여_어느_묶음도_알림_없이_소멸하지_않는다() {
        long m = kit.newMember();
        Instant now = kit.clock.instant();
        lotExpiringIn(m, now, Duration.ofDays(2), 10);
        long late = lotExpiringIn(m, now, Duration.ofDays(4), 20); // 오늘은 기간(3일) 밖, 내일 들어온다
        kit.withSetting("expiry_notice_days", 3, () -> kit.withSetting("expiry_notice_interval_days", 30, () -> {
            try {
                assertThat(batch.runFor(job, m).status()).isEqualTo(BatchRunStatus.OK);
                kit.clock.set(now.plus(Duration.ofDays(1)));
                assertThat(batch.runFor(job, m).status()).as("간격 min(30, 3 − 1) = 2일 — 다음 날은 쉰다").isEqualTo(BatchRunStatus.SKIP);
                kit.clock.set(now.plus(Duration.ofDays(2)));
                assertThat(batch.runFor(job, m).status()).as("소멸 이틀 전에 알린다").isEqualTo(BatchRunStatus.OK);
            } finally {
                kit.clock.set(now);
            }
        }));
        assertThat(noticedAt(late)).isNotNull();
        assertThat(notices(m)).hasSize(2);
    }

    @Test
    void 마지막_알림_시각은_조건부로_적어_겹친_실행이_한_번만_알린다() {
        long m = kit.newMember();
        Instant now = kit.clock.instant();
        lotExpiringIn(m, now, Duration.ofDays(10), 10);
        Instant restSince = now.minus(Duration.ofDays(6));
        assertThat(batch.queries.claimNotice(m, now, restSince)).isTrue();
        assertThat(batch.queries.claimNotice(m, now.plusSeconds(1), restSince)).as("이미 적은 뒤의 두 번째 실행").isFalse();
    }

    @Test
    void 알림_일수가_0_이면_꺼져_아무것도_하지_않는다() {
        long m = kit.newMember();
        Instant now = kit.clock.instant();
        long soon = lotExpiringIn(m, now, Duration.ofDays(5), 30);

        kit.withSetting("expiry_notice_days", 0, () -> {
            RunReport report = batch.runFor(job, m);
            assertThat(report.status()).isEqualTo(BatchRunStatus.SKIP);
            assertThat(batch.run(report.runId()).get("message_key")).isEqualTo("batch.result.pointExpiryNotice.off");
        });

        assertThat(notices(m)).isEmpty();
        assertThat(noticedAt(soon)).isNull();
    }

    @Test
    void 알림_일수는_정책값이다() {
        long m = kit.newMember();
        Instant now = kit.clock.instant();
        long in7 = lotExpiringIn(m, now, Duration.ofDays(7), 10);
        long in9 = lotExpiringIn(m, now, Duration.ofDays(9), 20);

        kit.withSetting("expiry_notice_days", 8, () -> batch.runFor(job, m));

        assertThat(noticedAt(in7)).isNotNull();
        assertThat(noticedAt(in9)).isNull();
        assertThat(number(payload(notices(m).get(0)).get("amount"))).isEqualTo(10);
        assertThat(number(payload(notices(m).get(0)).get("days"))).isEqualTo(8);
    }

    @Test
    void 지난_묶음과_이미_소멸한_묶음은_알리지_않는다() {
        long m = kit.newMember();
        Instant now = kit.clock.instant();
        long overdue = lotExpiringIn(m, now, Duration.ofDays(-1), 50); // 소멸 배치가 아직 안 돈 지난 묶음
        long spent = lotExpiringIn(m, now, Duration.ofDays(3), 20);
        kit.use(m, 20); // 무상 소멸 임박순 — 지난 묶음은 먼저 소멸되고 spent 에서 20 을 쓴다

        RunReport report = batch.runFor(job, m);

        assertThat(report.status()).isEqualTo(BatchRunStatus.SKIP);
        assertThat(notices(m)).isEmpty();
        assertThat(noticedAt(overdue)).isNull();
        assertThat(noticedAt(spent)).isNull();
        kit.assertInvariants(m);
    }

    @Test
    void 운영자가_이벤트를_끄면_알림은_없고_묶음은_알린_것으로_적는다() {
        long m = kit.newMember();
        Instant now = kit.clock.instant();
        long soon = lotExpiringIn(m, now, Duration.ofDays(5), 30);
        kit.jdbc.update("INSERT INTO member_event_settings (code, enabled, in_app, email, member_can_turn_off, updated_at) VALUES ('POINTS_EXPIRING', false, true, false, false, now()) "
                + "ON CONFLICT (code) DO UPDATE SET enabled = false");
        try {
            RunReport report = batch.runFor(job, m);

            assertThat(report.status()).isEqualTo(BatchRunStatus.OK);
            assertThat(notices(m)).isEmpty();
            assertThat(noticedAt(soon)).as("다음 날 다시 고르지 않는다").isNotNull();
        } finally {
            kit.jdbc.update("DELETE FROM member_event_settings WHERE code = 'POINTS_EXPIRING'");
        }
    }

    @Test
    void 탈퇴한_회원에게는_알리지_않는다() {
        long m = kit.newMember();
        Instant now = kit.clock.instant();
        lotExpiringIn(m, now, Duration.ofDays(5), 30);
        kit.jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", m);

        batch.runFor(job, m);

        assertThat(notices(m)).isEmpty();
    }

    @Test
    void 회원_알림_종류_CHECK_는_POINTS_EXPIRING_과_기존_종류를_모두_받는다() {
        long m = kit.newMember();
        for (MemberNotificationType type : MemberNotificationType.values()) {
            assertThatCode(() -> kit.jdbc.update("INSERT INTO member_notifications (id, user_id, type, created_at) VALUES (?, ?, ?, now())", kit.ids.nextId(), m,
                    type.name())).as(type.name()).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO member_notifications (id, user_id, type, created_at) VALUES (?, ?, 'NOT_A_TYPE', now())", kit.ids.nextId(), m))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
