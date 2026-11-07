package com.community.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.notification.repository.master.NotificationDeliveryRepository;
import com.community.testsupport.JpaTestContext;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 이벤트의 도구·추가 수신 팀 연결 표({@link NotificationDeliveryRepository}) — 관리 화면의 통째로 바꾸기·읽기·수 세기가 연결 엔티티·QueryDSL 로 바뀐 뒤에도
 * 예전 SQL 과 같은 결과·차례다. 실제 PostgreSQL.
 */
class NotificationDeliveryRepositoryTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(944);

    private NotificationDeliveryRepository delivery;
    private TransactionTemplate tx;
    private JdbcTemplate jdbc;
    private String eventA;
    private String eventB;
    private long channel1;
    private long channel2;
    private long team1;
    private long team2;

    @BeforeEach
    void setUp() {
        delivery = JpaTestContext.create(NotificationDeliveryRepository.class);
        tx = new TransactionTemplate(JpaTestContext.transactions());
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        long seed = IDS.nextId();
        eventA = "ZZ_DELIVERY_A_" + seed;
        eventB = "ZZ_DELIVERY_B_" + seed;
        jdbc.update("INSERT INTO notification_events (code, enabled, email_enabled, browser_enabled) VALUES (?, true, true, false), (?, true, false, true)", eventA, eventB);
        channel2 = IDS.nextId();
        channel1 = IDS.nextId();
        for (long id : new long[] {channel1, channel2}) {
            jdbc.update("INSERT INTO notification_channels (id, channel_type, channel_name) VALUES (?, 'SLACK', ?)", id, "ch-" + id);
        }
        team1 = IDS.nextId();
        team2 = IDS.nextId();
        for (long id : new long[] {team1, team2}) {
            jdbc.update("INSERT INTO teams (id, team_name, sort_order) VALUES (?, ?, 0)", id, "team-" + id);
        }
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM notification_events WHERE code IN (?, ?)", eventA, eventB);
        jdbc.update("DELETE FROM notification_channels WHERE id IN (?, ?)", channel1, channel2);
        jdbc.update("DELETE FROM teams WHERE id IN (?, ?)", team1, team2);
    }

    @Test
    void 통째로_바꾸면_예전_선택은_사라지고_id_차례로_읽는다() {
        tx.executeWithoutResult(s -> {
            delivery.replaceChannels(eventA, List.of(channel2, channel1, channel2));
            delivery.replaceTeams(eventA, List.of(team1));
            delivery.replaceChannels(eventB, List.of(channel1));
        });
        assertThat(delivery.channelIdsOf(eventA)).containsExactly(Math.min(channel1, channel2), Math.max(channel1, channel2));
        assertThat(delivery.teamIdsOf(eventA)).containsExactly(team1);
        assertThat(delivery.channelIdsByEvent().get(eventA)).containsExactly(Math.min(channel1, channel2), Math.max(channel1, channel2));
        assertThat(delivery.teamIdsByEvent().get(eventA)).containsExactly(team1);
        assertThat(delivery.eventCountByChannel().get(channel1)).isEqualTo(2);
        assertThat(delivery.eventCountByChannel().get(channel2)).isEqualTo(1);
        assertThat(delivery.eventCountByTeam().get(team1)).isEqualTo(1);

        tx.executeWithoutResult(s -> {
            delivery.replaceChannels(eventA, List.of(channel1));
            delivery.replaceTeams(eventA, List.of(team2, team1));
            // 같은 트랜잭션의 읽기가 바뀐 선택을 본다(넣기는 질의 전에 나간다)
            assertThat(delivery.channelIdsOf(eventA)).containsExactly(channel1);
        });
        assertThat(delivery.channelIdsOf(eventA)).containsExactly(channel1);
        assertThat(delivery.teamIdsOf(eventA)).containsExactly(Math.min(team1, team2), Math.max(team1, team2));
        assertThat(delivery.eventCountByChannel()).doesNotContainKey(channel2);

        tx.executeWithoutResult(s -> delivery.replaceChannels(eventB, List.of()));
        assertThat(delivery.channelIdsOf(eventB)).isEmpty();
        assertThat(delivery.channelIdsByEvent()).doesNotContainKey(eventB);
    }

    @Test
    void 이메일_브라우저를_켠_이벤트_수는_표_전체에서_센다() {
        assertThat(delivery.emailEventCount()).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM notification_events WHERE email_enabled", Integer.class));
        assertThat(delivery.browserEventCount()).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM notification_events WHERE browser_enabled", Integer.class));
        assertThat(delivery.emailEventCount()).isPositive();
    }
}
