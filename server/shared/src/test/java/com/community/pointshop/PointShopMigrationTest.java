package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.flyway.SchemaMigration;
import java.util.Arrays;
import java.util.List;
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
 * 포인트 상점 마이그레이션(가번호 V86 — 설명 「point shop」으로 찾는다)의 <b>옮기기</b> — 같은 시험 컨테이너에 빈 DB 를 하나 더 만들어 바로 앞 번호까지 이관하고, 0-5 의 옛 이름
 * (요청 제한 {@code SHOP_PURCHASE}·권한 {@code shop.*}·역할에 준 권한)과 옛 변경권 가격({@code point_settings.nickname_ticket_price})을 넣은 뒤 적용해 본다.
 *
 * <p>확인: 요청 제한 행(한도 값 그대로)·멱등 키 행의 종류 이름이 {@code POINT_SHOP_PURCHASE}, 권한 행이 {@code pointshop.*}(분류 그대로·기능 준비됨)이고 역할에 준 것도 옮겨졌다(이미 새 이름이
 * 있던 역할은 한 줄), 옛 이름은 어디에도 없다. 변경권 품목 가격 = 옛 정책 값, 정책 칸은 지워졌고 남은 범위 CHECK 는 그대로다. 아바타 306 · 설정 1행(7일).
 */
class PointShopMigrationTest {

    private static final String DB = "point_shop_mig_" + Long.toString(System.nanoTime(), 36);

    private static JdbcTemplate admin;
    private static DriverManagerDataSource legacy;
    private static JdbcTemplate jdbc;
    private static MigrationVersion pointShop;

    @BeforeAll
    static void setUp() {
        admin = new JdbcTemplate(CommerceTestDatabase.migrated());
        admin.execute("CREATE DATABASE " + DB);
        legacy = new DriverManagerDataSource(CommerceTestDatabase.jdbcUrl().replaceFirst("/roboyam(?=\\?|$)", "/" + DB), CommerceTestDatabase.username(),
                CommerceTestDatabase.password());
        legacy.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(legacy);

        MigrationInfo[] all = flyway(null).info().all();
        pointShop = Arrays.stream(all).filter(info -> "point shop".equals(info.getDescription())).findFirst().orElseThrow().getVersion();
        MigrationVersion before = Arrays.stream(all).map(MigrationInfo::getVersion).filter(v -> v != null && v.compareTo(pointShop) < 0).max(MigrationVersion::compareTo)
                .orElseThrow();
        flyway(before).migrate();

        // ── 0-5 의 옛 이름(관리자 서버가 뜰 때 카탈로그로 넣었던 모양) ──
        perm("shop.read", "OPERATOR", "gradePoint", "READ");
        perm("shop.manage", "OPERATOR", "gradePoint", "MANAGE");
        perm("shop.refund", "OPERATOR", "gradePoint", "SENSITIVE");
        perm("shop.use", "MEMBER", "memberSite", "USE");
        perm("pointshop.read", "OPERATOR", "gradePoint", "READ"); // 다른 서버가 먼저 새 이름을 넣은 경우
        role("ROLE_PS_OPERATOR", "op_gradePoint", "OPERATOR");
        role("ROLE_PS_BOTH", "op_gradePoint", "OPERATOR");
        role("ROLE_PS_MEMBER", "memberCustom", "MEMBER");
        grant(1, "ROLE_PS_OPERATOR", "shop.read");
        grant(2, "ROLE_PS_OPERATOR", "shop.refund");
        grant(3, "ROLE_PS_BOTH", "shop.read");
        grant(4, "ROLE_PS_BOTH", "pointshop.read");
        grant(5, "ROLE_PS_MEMBER", "shop.use");
        jdbc.update("UPDATE request_rate_limits SET limit_per_minute = 7 WHERE action = 'SHOP_PURCHASE'");
        jdbc.update("INSERT INTO idempotency_requests (user_id, idem_key, action, request_hash, status) VALUES (1, 'mig-key-0001', 'SHOP_PURCHASE', ?, 'IN_PROGRESS')",
                "0".repeat(64));
        jdbc.update("INSERT INTO request_rate_counters (user_id, action, window_start, count) VALUES (1, 'SHOP_PURCHASE', date_trunc('minute', now()), 3)");
        jdbc.update("UPDATE point_settings SET nickname_ticket_price = 1500 WHERE id = 1");

        flyway(pointShop).migrate();
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
            // 옛 판까지 올릴 때는 반복 마이그레이션(R__)을 빼고 올린다 — R__ 는 늘 지금 판 표를 가정해(예: R__batch_24 가 소모임 V90 의 group_id) 옛 판에서 실패한다.
            // 반복 마이그레이션은 마지막 전체 migrate 가 모두 적용한다.
            configuration.target(target).repeatableSqlMigrationPrefix("NOT_A_REPEATABLE__");
        }
        return configuration.load();
    }

    private static void perm(String code, String axis, String type, String category) {
        jdbc.update("INSERT INTO permissions (code, axis, type_id, category, feature_ready) VALUES (?, ?, ?, ?, false)", code, axis, type, category);
    }

    private static void role(String id, String type, String axis) {
        jdbc.update("INSERT INTO roles (role_id, role_type_id, axis, role_name) VALUES (?, ?, ?, ?)", id, type, axis, id);
    }

    private static void grant(long id, String role, String code) {
        jdbc.update("INSERT INTO role_permissions (id, role_id, permission_code) VALUES (?, ?, ?)", id, role, code);
    }

    private static List<String> codesOf(String role) {
        return jdbc.queryForList("SELECT permission_code FROM role_permissions WHERE role_id = ? ORDER BY permission_code", String.class, role);
    }

    @Test
    void 요청_제한_종류_SHOP_PURCHASE_는_POINT_SHOP_PURCHASE_로_한도_값은_그대로() {
        assertThat(jdbc.queryForList("SELECT action FROM request_rate_limits WHERE action LIKE '%SHOP%'", String.class)).containsExactly("POINT_SHOP_PURCHASE");
        assertThat(jdbc.queryForObject("SELECT limit_per_minute FROM request_rate_limits WHERE action = 'POINT_SHOP_PURCHASE'", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT action FROM idempotency_requests WHERE idem_key = 'mig-key-0001'", String.class)).isEqualTo("POINT_SHOP_PURCHASE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM request_rate_counters WHERE action = 'SHOP_PURCHASE'", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT action FROM request_rate_limits ORDER BY action", String.class))
                .containsExactlyInAnyOrder("GROUPBUY_JOIN", "POINT_CHARGE", "POINT_SHOP_PURCHASE", "REFUND_REQUEST");
    }

    @Test
    void 권한_shop_은_pointshop_으로_분류_그대로_역할에_준_것도_옮긴다() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code LIKE 'shop.%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permissions WHERE permission_code LIKE 'shop.%'", Integer.class)).isZero();
        Map<String, Object> refund = jdbc.queryForMap("SELECT axis, type_id, category, feature_ready FROM permissions WHERE code = 'pointshop.refund'");
        assertThat(refund).containsEntry("axis", "OPERATOR").containsEntry("type_id", "gradePoint").containsEntry("category", "SENSITIVE").containsEntry("feature_ready", true);
        assertThat(jdbc.queryForMap("SELECT axis, type_id, category FROM permissions WHERE code = 'pointshop.use'")).containsEntry("axis", "MEMBER")
                .containsEntry("type_id", "memberSite").containsEntry("category", "USE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code = 'pointshop.manage'", Integer.class)).isEqualTo(1);

        assertThat(codesOf("ROLE_PS_OPERATOR")).containsExactly("pointshop.read", "pointshop.refund");
        assertThat(codesOf("ROLE_PS_BOTH")).as("이미 새 이름이 있던 역할은 한 줄").containsExactly("pointshop.read");
        assertThat(codesOf("ROLE_PS_MEMBER")).containsExactly("pointshop.use");
    }

    @Test
    void 변경권_품목_가격은_옛_정책_값이고_정책_칸은_지워지고_남은_범위_CHECK_는_그대로() {
        assertThat(jdbc.queryForMap("SELECT kind, usage_type, price_point FROM point_shop_items WHERE code = 'nickname-change'")).containsEntry("kind", "NICKNAME_CHANGE_TICKET")
                .containsEntry("usage_type", "CONSUMABLE").containsEntry("price_point", 1500);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name = 'point_settings' AND column_name = 'nickname_ticket_price'",
                Integer.class)).isZero();
        assertThatThrownBy(() -> jdbc.update("UPDATE point_settings SET like_bonus_threshold = 0 WHERE id = 1")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE point_settings SET popular_reward_top_n = 51 WHERE id = 1")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void 아바타_306종_설정_1행_시드_ID_는_마이그레이션용_노드_1023_의_Snowflake() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_shop_items WHERE kind = 'PROFILE_AVATAR'", Integer.class)).isEqualTo(306);
        assertThat(jdbc.queryForObject("SELECT refund_days FROM point_shop_settings WHERE id = 1", Integer.class)).isEqualTo(7);
        List<Long> ids = jdbc.queryForList("SELECT id FROM point_shop_items ORDER BY id", Long.class);
        assertThat(ids).hasSize(307).allSatisfy(id -> assertThat((id >> 12) & 1023).as("노드 비트").isEqualTo(1023L));
        long ticket = jdbc.queryForObject("SELECT id FROM point_shop_items WHERE code = 'nickname-change'", Long.class);
        assertThat(ticket & 4095).as("변경권 순번 0").isZero();
        assertThat(jdbc.queryForObject("SELECT id FROM point_shop_items WHERE code = 'avatar-job-doctor-m'", Long.class) - ticket).as("아바타 순번 = 카탈로그 차례").isEqualTo(1);
    }
}
