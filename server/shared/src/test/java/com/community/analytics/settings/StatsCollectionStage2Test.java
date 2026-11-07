package com.community.analytics.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.JpaTestContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 통계 수집 설정 2단계 세 칸(shared V135 — site-analytics-contract 「2단계」): 기본 꺼짐, 방문 수집 전체가 꺼지면 동작하지 않음, 저장·읽기에 새 칸이 빠지지 않음.
 * 설정은 한 행이라 시험이 끝나면 모두 꺼짐 · 90일로 되돌린다.
 */
class StatsCollectionStage2Test {

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void reset() {
        jdbc.update("UPDATE stats_collection_settings SET enabled = false, visits = false, raw_events = false, raw_retention_days = 90, referrers = false, flows = false, "
                + "environment = false, search_terms = false, bots = false, notification_sends = false, board_activity = false WHERE id = 1");
    }

    @Test
    void V135_새_칸_세_개는_NOT_NULL_기본_false() {
        List<Map<String, Object>> cols = jdbc.queryForList("SELECT column_name, is_nullable, column_default FROM information_schema.columns "
                + "WHERE table_name = 'stats_collection_settings' AND column_name IN ('environment', 'search_terms', 'bots') ORDER BY column_name");
        assertThat(cols).hasSize(3);
        for (Map<String, Object> c : cols) {
            assertThat(c.get("is_nullable")).as("%s", c.get("column_name")).isEqualTo("NO");
            assertThat(c.get("column_default")).as("%s", c.get("column_name")).isEqualTo("false");
        }
        assertThat(JpaTestContext.create(StatsCollectionStore.class).find().orElseThrow().value()).isEqualTo(StatsCollection.OFF);
    }

    @Test
    void 일단계_생성자는_새_칸을_꺼짐으로_두고_OFF_는_모두_꺼짐() {
        StatsCollection s = new StatsCollection(true, true, true, 90, true, true, true, true);
        assertThat(s.environment()).isFalse();
        assertThat(s.searchTerms()).isFalse();
        assertThat(s.bots()).isFalse();
        assertThat(s.notificationSends()).isTrue();
        assertThat(s.boardActivity()).isTrue();
        assertThat(StatsCollection.OFF.anyCollectOn()).isFalse();
        assertThat(StatsCollection.OFF.environment() || StatsCollection.OFF.searchTerms() || StatsCollection.OFF.bots()).isFalse();
    }

    @Test
    void 새_칸은_방문_수집_전체가_켜졌을_때만_동작하고_검색어_봇은_방문_신호가_아니다() {
        StatsCollection offAll = new StatsCollection(false, false, false, 90, false, false, true, true, true, false, false);
        assertThat(offAll.environmentOn()).isFalse();
        assertThat(offAll.searchTermsOn()).isFalse();
        assertThat(offAll.botsOn()).isFalse();
        assertThat(offAll.anyVisitOn()).isFalse();
        assertThat(offAll.anyCollectOn()).isFalse();

        StatsCollection searchOnly = new StatsCollection(true, false, false, 90, false, false, false, true, false, false, false);
        assertThat(searchOnly.searchTermsOn()).isTrue();
        assertThat(searchOnly.anyVisitOn()).as("검색어는 방문 신호가 아니다 — 브라우저 스위치에 들지 않는다").isFalse();
        assertThat(searchOnly.anyCollectOn()).isTrue();

        StatsCollection botsOnly = new StatsCollection(true, false, false, 90, false, false, false, false, true, false, false);
        assertThat(botsOnly.anyVisitOn()).isFalse();
        assertThat(botsOnly.anyCollectOn()).isTrue();

        StatsCollection envOnly = new StatsCollection(true, false, false, 90, false, false, true, false, false, false, false);
        assertThat(envOnly.anyVisitOn()).as("환경은 신호에 싣는다").isTrue();

        StatsCollection enabledOnly = new StatsCollection(true, false, false, 90, false, false, false, false, false, true, true);
        assertThat(enabledOnly.anyCollectOn()).as("알림·게시판 활동은 방문 쪽 수집이 아니다").isFalse();
    }

    @Test
    void 저장하고_읽으면_새_칸이_그대로_돌아온다() {
        StatsCollectionStore store = JpaTestContext.create(StatsCollectionStore.class);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        StatsCollection v = new StatsCollection(true, false, true, 180, false, true, true, false, true, false, true);
        TransactionTemplate tx = new TransactionTemplate(JpaTestContext.transactions());
        tx.executeWithoutResult(s -> store.save(v, 9_800_000_001L, now));
        StatsCollectionStore.Stored got = store.find().orElseThrow();
        assertThat(got.value()).isEqualTo(v);
        assertThat(got.updatedAt()).isEqualTo(now);
        assertThat(got.updatedBy()).isEqualTo(9_800_000_001L);
        assertThat(jdbc.queryForObject("SELECT environment::text || search_terms::text || bots::text FROM stats_collection_settings WHERE id = 1", String.class))
                .isEqualTo("truefalsetrue");

        StatsCollection w = new StatsCollection(true, false, true, 180, false, true, false, true, false, false, true);
        tx.executeWithoutResult(s -> store.save(w, 9_800_000_001L, now.plusSeconds(1)));
        assertThat(store.find().orElseThrow().value()).isEqualTo(w);
    }
}
