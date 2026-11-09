package com.community.analytics.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.analytics.settings.StatsCollection;
import com.community.analytics.settings.StatsCollectionSettings;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * 알림 발송 기록(site-analytics-contract 「수집·쓰기」 — 메일 MailDispatcher · Slack · Mattermost 발송 지점이 부른다) — 실제 PostgreSQL. 시험마다 자기만의 채널 이름을 써서
 * 다른 시험·발송의 줄과 섞이지 않는다(채널 칸은 글자라 받는다).
 */
class NotificationSendStatsTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(914);
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    private static final StatsCollection SENDS_ON = new StatsCollection(false, false, false, 90, false, false, true, false);

    private static StatsCollectionSettings settings(AtomicReference<StatsCollection> ref) {
        return new StatsCollectionSettings(null, null) {
            @Override
            public StatsCollection current() {
                return ref.get();
            }
        };
    }

    /** 연결을 끊었다 붙였다 할 수 있는 데이터소스 */
    static final class Switchable extends DelegatingDataSource {

        final AtomicBoolean down = new AtomicBoolean();

        Switchable(DataSource target) {
            super(target);
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (down.get()) {
                throw new SQLException("시험 — DB 연결 끊김");
            }
            return super.getConnection();
        }
    }

    private static String channel() {
        return "QA" + Long.toString(ThreadLocalRandom.current().nextLong(1L << 40, 1L << 50), 36).toUpperCase();
    }

    private static NotificationSendStats stats(AtomicReference<StatsCollection> ref, DataSource ds) {
        return new NotificationSendStats(settings(ref), IDS, ds, new DataSourceTransactionManager(ds));
    }

    /** 오늘(한국 날짜) 그 채널 [sent, failed] — 줄이 없으면 null */
    private static long[] daily(String channel) {
        List<long[]> rows = jdbc.query("SELECT sent, failed FROM notification_send_daily WHERE day = ? AND channel = ?", (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)},
                Date.valueOf(ServiceTime.dateOf(Instant.now())), channel);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Test
    void 성공_실패를_세어_1분마다_더하고_실패는_사유_코드와_출처만_남긴다() {
        String channel = channel();
        NotificationSendStats stats = stats(new AtomicReference<>(SENDS_ON), dataSource);
        stats.sent(channel);
        stats.sent(channel);
        stats.sent(channel);
        stats.failed(channel, "AUTH_FAILED", null);
        stats.failed(channel, null, "1234567890");

        stats.flush();
        assertThat(daily(channel)).containsExactly(3, 2);
        assertThat(jdbc.queryForList("SELECT reason_code FROM notification_send_failures WHERE channel = ? ORDER BY reason_code", String.class, channel))
                .containsExactly("AUTH_FAILED", "ERROR");
        assertThat(jdbc.queryForList("SELECT source FROM notification_send_failures WHERE channel = ? AND source IS NOT NULL", String.class, channel))
                .containsExactly("1234567890");

        stats.flush(); // 빈 묶음 — 두 번 더하지 않는다
        stats.sent(channel);
        stats.flush();
        assertThat(daily(channel)).containsExactly(4, 2);
    }

    @Test
    void 수신자_본문을_담을_칸이_없다() {
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'notification_send_failures' ORDER BY ordinal_position",
                String.class)).containsExactly("id", "failed_at", "channel", "reason_code", "source");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'notification_send_daily' ORDER BY ordinal_position",
                String.class)).containsExactly("day", "channel", "sent", "failed");
    }

    @Test
    void 긴_사유_코드와_출처는_40자로_자른다() {
        String channel = channel();
        NotificationSendStats stats = stats(new AtomicReference<>(SENDS_ON), dataSource);
        stats.failed(channel, "R".repeat(60), "S".repeat(60));
        stats.flush();
        assertThat(jdbc.queryForMap("SELECT reason_code, source FROM notification_send_failures WHERE channel = ?", channel))
                .containsEntry("reason_code", "R".repeat(40)).containsEntry("source", "S".repeat(40));
    }

    @Test
    void 설정이_꺼져_있으면_세지_않고_방문_수집_전체와는_무관하다() {
        String channel = channel();
        AtomicReference<StatsCollection> ref = new AtomicReference<>(StatsCollection.OFF);
        NotificationSendStats stats = stats(ref, dataSource);
        stats.sent(channel);
        stats.failed(channel, "ERROR", null);
        stats.flush();
        assertThat(daily(channel)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_send_failures WHERE channel = ?", Long.class, channel)).isZero();

        // 방문 수집 전체(enabled)가 꺼져도 알림 발송 기록만 켜져 있으면 센다
        ref.set(SENDS_ON);
        stats.sent(channel);
        stats.flush();
        assertThat(daily(channel)).containsExactly(1, 0);
    }

    @Test
    void 쓰기가_실패하면_메모리에_되돌려_다음에_한_번만_쓴다() {
        String channel = channel();
        Switchable ds = new Switchable(dataSource);
        NotificationSendStats stats = stats(new AtomicReference<>(SENDS_ON), ds);
        stats.sent(channel);
        stats.failed(channel, "MAIL_FAILED", null);
        ds.down.set(true);
        stats.flush();
        assertThat(daily(channel)).isNull();

        stats.sent(channel); // 그 사이 한 통 더
        ds.down.set(false);
        stats.flush();
        assertThat(daily(channel)).containsExactly(2, 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_send_failures WHERE channel = ?", Long.class, channel)).isEqualTo(1);
        stats.flush();
        assertThat(daily(channel)).containsExactly(2, 1);
    }
}
