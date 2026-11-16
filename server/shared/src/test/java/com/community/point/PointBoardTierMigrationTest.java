package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.flyway.SchemaMigration;
import java.util.Arrays;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 게시판 등급별 포인트 마이그레이션(설명 「point board tiers」, point-board-tier-contract)의 <b>전환</b> — 같은 시험 컨테이너에 빈 DB 를 하나 더 만들어 바로 앞 번호까지 이관하고 옛 값을 넣은 뒤
 * 적용해 본다. 확인: 게시판 pointMode ON → NORMAL(기본값·게시판), 기본 배정(뉴스·핫딜 = INFO, 팁·강좌·사용기 = CORE — 끈 게시판은 그대로), 일반 등급 추천 보너스 = 기존 전역 값,
 * 등급 칸 기본값, CHECK(옛 ON 거절 · 새 원장 사유와 종류 짝).
 */
class PointBoardTierMigrationTest {

    private static final String DB = "point_tier_" + Long.toString(System.nanoTime(), 36);

    private static JdbcTemplate admin;
    private static DriverManagerDataSource legacy;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        admin = new JdbcTemplate(CommerceTestDatabase.migrated());
        admin.execute("CREATE DATABASE " + DB);
        legacy = new DriverManagerDataSource(CommerceTestDatabase.jdbcUrl().replaceFirst("/irobit(?=\\?|$)", "/" + DB), CommerceTestDatabase.username(),
                CommerceTestDatabase.password());
        legacy.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(legacy);

        MigrationInfo[] all = flyway(null).info().all();
        MigrationVersion tiers = Arrays.stream(all).filter(info -> "point board tiers".equals(info.getDescription())).findFirst().orElseThrow().getVersion();
        MigrationVersion before = Arrays.stream(all).map(MigrationInfo::getVersion).filter(v -> v != null && v.compareTo(tiers) < 0).max(MigrationVersion::compareTo)
                .orElseThrow();
        flyway(before).migrate();

        // 옛 값: 기본값 ON, 게시판 — 뉴스(따름)·핫딜(ON)·팁(따름)·사용기(OFF)·자유(ON)·기타(따름), 전역 추천 보너스 7P
        jdbc.update("UPDATE board_defaults SET point_mode = 'ON' WHERE id = 1");
        jdbc.update("UPDATE point_settings SET like_bonus_points = 7 WHERE id = 1");
        board(1, "news", "GENERAL", null);
        board(2, "hotdeal", "DEAL", "ON");
        board(3, "tip", "GENERAL", null);
        board(4, "review", "GENERAL", "OFF");
        board(5, "free", "GENERAL", "ON");
        board(6, "humor", "GENERAL", null);

        flyway(tiers).migrate();
    }

    @AfterAll
    static void tearDown() {
        if (admin != null) {
            admin.execute("DROP DATABASE IF EXISTS " + DB + " WITH (FORCE)");
        }
    }

    private static Flyway flyway(MigrationVersion target) {
        var configuration = SchemaMigration.STANDARD.configure(Flyway.configure().dataSource(legacy), SchemaMigration.SHARED);
        if (target != null) {
            configuration.target(target).repeatableSqlMigrationPrefix("NOT_A_REPEATABLE__"); // 옛 판까지는 R__ 없이(PointMigrationConversionTest 와 같은 까닭)
        }
        return configuration.load();
    }

    private static void board(long id, String slug, String kind, String pointMode) {
        if (jdbc.update("UPDATE boards SET kind = ?, point_mode = ? WHERE slug = ?", kind, pointMode, slug) > 0) {
            return; // 이 판의 마이그레이션이 이미 만든 게시판(설치 프리셋 시드)이면 값만 맞춘다
        }
        jdbc.update("INSERT INTO boards (id, slug, name, sort_order, kind, point_mode) VALUES (?, ?, ?, ?, ?, ?)", 9_000_000_000L + id, slug, "전환 " + slug, (int) id, kind,
                pointMode);
    }

    private static String mode(String slug) {
        return jdbc.queryForObject("SELECT point_mode FROM boards WHERE slug = ?", String.class, slug);
    }

    @Test
    void 옛_ON_은_NORMAL_이고_기본_배정은_뉴스_핫딜_INFO_팁_사용기_CORE_끈_게시판은_그대로다() {
        assertThat(jdbc.queryForObject("SELECT point_mode FROM board_defaults WHERE id = 1", String.class)).isEqualTo("NORMAL");
        assertThat(mode("news")).isEqualTo("INFO");
        assertThat(mode("hotdeal")).isEqualTo("INFO");
        assertThat(mode("tip")).isEqualTo("CORE");
        assertThat(mode("review")).as("운영자가 끈 게시판").isEqualTo("OFF");
        assertThat(mode("free")).isEqualTo("NORMAL");
        assertThat(mode("humor")).as("기본값 따름 그대로").isNull();
    }

    @Test
    void 등급_칸은_계약_기본값이고_일반_추천_보너스는_기존_전역_값에서_시작한다() {
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM point_settings WHERE id = 1");
        assertThat(row).containsEntry("tier_normal_like_bonus_points", 7).containsEntry("tier_normal_featured_points", 0).containsEntry("tier_normal_min_images", 0)
                .containsEntry("tier_normal_hold_hours", 0)
                .containsEntry("tier_info_post_points", 30).containsEntry("tier_info_like_bonus_points", 50).containsEntry("tier_info_featured_points", 100)
                .containsEntry("tier_info_min_length", 100).containsEntry("tier_info_min_images", 0).containsEntry("tier_info_hold_hours", 24)
                .containsEntry("tier_core_post_points", 50).containsEntry("tier_core_like_bonus_points", 150).containsEntry("tier_core_featured_points", 500)
                .containsEntry("tier_core_min_length", 500).containsEntry("tier_core_min_images", 1).containsEntry("tier_core_hold_hours", 24)
                .containsEntry("info_source_once", true).containsEntry("exclude_ai_and_ops", true);
        assertThatThrownBy(() -> jdbc.update("UPDATE point_settings SET tier_core_hold_hours = 10001 WHERE id = 1")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void CHECK_는_옛_ON_을_거절하고_우수_글_사유의_종류_짝을_지킨다() {
        assertThatThrownBy(() -> jdbc.update("UPDATE boards SET point_mode = 'ON' WHERE slug = 'free'")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE board_defaults SET point_mode = 'ON' WHERE id = 1")).isInstanceOf(DataAccessException.class);
        jdbc.update("INSERT INTO users (id, terms_version, terms_agreed_at, age_confirmed_at) VALUES (9100000000000000001, 'v1', now(), now())");
        jdbc.update("INSERT INTO point_ledger (id, user_id, kind, reason, delta, free_delta, paid_delta, balance_after, ref_type, ref_id, created_at) "
                + "VALUES (9100000000000000011, 9100000000000000001, 'EARN', 'POST_FEATURED', 100, 100, 0, 100, 'POST', 1, now())");
        jdbc.update("INSERT INTO point_ledger (id, user_id, kind, reason, delta, free_delta, paid_delta, balance_after, ref_type, ref_id, created_at) "
                + "VALUES (9100000000000000012, 9100000000000000001, 'REVOKE', 'POST_FEATURED_REVOKE', -100, -100, 0, 0, 'POST', 1, now())");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO point_ledger (id, user_id, kind, reason, delta, free_delta, paid_delta, balance_after, ref_type, ref_id, created_at) "
                + "VALUES (9100000000000000013, 9100000000000000001, 'EARN', 'POST_FEATURED_REVOKE', 100, 100, 0, 100, 'POST', 2, now())"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO point_ledger (id, user_id, kind, reason, delta, free_delta, paid_delta, balance_after, ref_type, ref_id, created_at) "
                + "VALUES (9100000000000000014, 9100000000000000001, 'REVOKE', 'POST_FEATURED', -100, -100, 0, 0, 'POST', 3, now())"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void 보류_표는_대기_중인_같은_사유_대상을_하나만_받는다() {
        jdbc.update("INSERT INTO point_pending_grants (id, user_id, reason, ref_type, ref_id, amount, release_at, created_at) VALUES (1, 1, 'LIKE_BONUS', 'POST', 77, 50, now(), now())");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO point_pending_grants (id, user_id, reason, ref_type, ref_id, amount, release_at, created_at) "
                + "VALUES (2, 1, 'LIKE_BONUS', 'POST', 77, 50, now(), now())")).isInstanceOf(DataAccessException.class);
        jdbc.update("UPDATE point_pending_grants SET status = 'CANCELLED', cancel_reason = 'DELETED', settled_at = now() WHERE id = 1");
        jdbc.update("INSERT INTO point_pending_grants (id, user_id, reason, ref_type, ref_id, amount, release_at, created_at) VALUES (3, 1, 'LIKE_BONUS', 'POST', 77, 50, now(), now())");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO point_pending_grants (id, user_id, reason, ref_type, ref_id, amount, release_at, created_at) "
                + "VALUES (4, 1, 'POST_FEATURED', 'COMMENT', 78, 50, now(), now())")).as("우수 글은 글만").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO post_features (id, post_id, featured_by, featured_at) VALUES (11, 500, 1, now()), (12, 500, 1, now())"))
                .as("한 글에 살아 있는 선정은 하나").isInstanceOf(DataAccessException.class);
    }
}
