package com.community.commerce.guard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.commerce.guard.dto.RequestAction;
import com.community.testsupport.CommerceTestDatabase;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 요청 제한 값의 15초 사본(commerce.md 12-4 「서버들은 설정 사본을 최대 15초 안에」) — 실제 PostgreSQL. 바꾼 한도는 시험 끝에 기본값으로 되돌린다 */
class RequestRateLimitsTest {

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void restore() {
        for (RequestAction action : RequestAction.values()) {
            jdbc.update("INSERT INTO request_rate_limits (action, limit_per_minute) VALUES (?, ?) ON CONFLICT (action) DO UPDATE SET limit_per_minute = EXCLUDED.limit_per_minute",
                    action.name(), action.defaultLimit());
        }
    }

    @Test
    void 사본은_15초_동안_다시_읽지_않고_비우면_바로_다시_읽는다() {
        RequestRateLimits limits = new RequestRateLimits(dataSource);
        assertThat(limits.limitOf(RequestAction.SHOP_PURCHASE)).isEqualTo(10);
        assertThat(RequestRateLimits.TTL_SECONDS).isEqualTo(15);

        jdbc.update("UPDATE request_rate_limits SET limit_per_minute = 42 WHERE action = 'SHOP_PURCHASE'");
        assertThat(limits.limitOf(RequestAction.SHOP_PURCHASE)).as("다른 서버가 바꾼 값은 사본 주기 안에는 아직 옛 값").isEqualTo(10);

        limits.invalidate();
        assertThat(limits.limitOf(RequestAction.SHOP_PURCHASE)).as("저장한 서버는 커밋 뒤 비워 바로 새 값").isEqualTo(42);
    }

    @Test
    void 행이_없는_종류는_기본값() {
        jdbc.update("DELETE FROM request_rate_limits WHERE action = 'REFUND_REQUEST'");
        RequestRateLimits limits = new RequestRateLimits(dataSource);
        assertThat(limits.limitOf(RequestAction.REFUND_REQUEST)).isEqualTo(3);
    }

    @Test
    void DB_는_0_에서_100_밖의_한도를_막는다() {
        assertThatThrownBy(() -> jdbc.update("UPDATE request_rate_limits SET limit_per_minute = 101 WHERE action = 'POINT_CHARGE'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE request_rate_limits SET limit_per_minute = -1 WHERE action = 'POINT_CHARGE'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE request_rate_limits SET limit_per_minute = 0 WHERE action = 'POINT_CHARGE'");
        jdbc.update("UPDATE request_rate_limits SET limit_per_minute = 100 WHERE action = 'POINT_CHARGE'");
    }
}
