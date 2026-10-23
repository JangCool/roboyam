package com.community.batch.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 짧은 주기 워커의 기록(docs/domain/commerce.md 15-4 — 실행마다 로그를 쌓지 않고 {@code worker_heartbeats} 한 줄만, 결정 16 — 기록만). 실제 PostgreSQL.
 */
class WorkerHeartbeatsTest {

    private static BatchTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = new BatchTestKit();
    }

    /** 시험이 움직이는 시계 */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-23T03:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static Map<String, Object> row(String key) {
        return kit.jdbc.queryForMap("SELECT * FROM worker_heartbeats WHERE worker_key = ?", key);
    }

    private static Instant at(Object value) {
        return value == null ? null : ((java.sql.Timestamp) value).toInstant();
    }

    @Test
    void 성공은_건수와_시각을_남기고_한가할_때는_간격마다_한_번만_쓴다() {
        String key = BatchTestKit.newKey("beat");
        MovableClock clock = new MovableClock();
        WorkerHeartbeats heartbeats = new WorkerHeartbeats(kit.dataSource, BatchTestKit.NODE_ID, Duration.ofSeconds(30), clock);

        assertThat(heartbeats.success(key, 0)).as("처음은 쓴다").isTrue();
        Instant firstWrite = at(row(key).get("updated_at"));
        clock.advance(Duration.ofSeconds(2));
        assertThat(heartbeats.success(key, 0)).as("일이 없고 30초가 안 됐다 — 쓰지 않는다").isFalse();
        assertThat(at(row(key).get("updated_at"))).isEqualTo(firstWrite);

        clock.advance(Duration.ofSeconds(2));
        assertThat(heartbeats.success(key, 7)).as("일을 했으면 늘 쓴다").isTrue();
        assertThat(row(key).get("last_count")).isEqualTo(7L);
        assertThat(row(key).get("node_id")).isEqualTo(BatchTestKit.NODE_ID);

        clock.advance(Duration.ofSeconds(10));
        assertThat(heartbeats.success(key, 0)).isFalse();
        clock.advance(Duration.ofSeconds(21));
        assertThat(heartbeats.success(key, 0)).as("마지막 기록 뒤 30초가 지났다").isTrue();
        assertThat(row(key).get("last_count")).isEqualTo(0L);
        assertThat(at(row(key).get("last_success_at"))).isEqualTo(clock.instant());
    }

    @Test
    void 실패는_가린_오류를_남기고_마지막_성공은_그대로_둔다() {
        String key = BatchTestKit.newKey("beat");
        MovableClock clock = new MovableClock();
        WorkerHeartbeats heartbeats = new WorkerHeartbeats(kit.dataSource, BatchTestKit.NODE_ID, Duration.ofSeconds(30), clock);
        heartbeats.success(key, 3);
        Instant success = clock.instant();

        clock.advance(Duration.ofSeconds(5));
        assertThat(heartbeats.failure(key, new IllegalStateException("메일 kim@example.com 실패 password=pw123"))).isTrue();

        Map<String, Object> row = row(key);
        assertThat((String) row.get("last_error")).startsWith("IllegalStateException").doesNotContain("kim@example.com").doesNotContain("pw123");
        assertThat(at(row.get("last_error_at"))).isEqualTo(clock.instant());
        assertThat(at(row.get("last_success_at"))).as("마지막 성공 시각은 그대로 — 멈춤 알람(4단계)이 이 값을 본다").isEqualTo(success);
        assertThat(row.get("last_count")).isEqualTo(3L);
    }
}
