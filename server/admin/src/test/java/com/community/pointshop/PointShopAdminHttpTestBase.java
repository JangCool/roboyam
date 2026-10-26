package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.point.service.PointService;
import com.community.pointshop.dto.PointShopBuyer;
import com.community.pointshop.dto.request.PointShopItemSaveRequest;
import com.community.pointshop.dto.response.PointShopPurchaseResultView;
import com.community.pointshop.service.PointShopCatalogService;
import com.community.pointshop.service.PointShopPurchaseService;
import com.community.testsupport.MutableTestClock;
import com.community.testsupport.PointInvariants;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커머스 1-2 관리자 포인트 상점 API 시험의 바탕 — {@link Commerce04AdminTestBase}(실제 필터 체인·세션·권한 인터셉터·PostgreSQL) 위에서 시험 데이터를 <b>실제 서비스</b>로 만든다
 * (포인트 지급 {@link PointService#adjust}, 품목 {@link PointShopCatalogService#create}, 구매 {@link PointShopPurchaseService#purchase}).
 * 시계는 옮길 수 있다({@link MutableTestClock} — 앱의 포인트·포인트 상점 서비스가 같은 시계를 쓴다. 구매 시각은 트리거가 고치지 못하게 막으므로 지난 구매는 시계를 옮겨 그때 산다).
 * DB 를 다른 시험과 같이 쓰므로 회원·품목은 새로 만들고, 공용 행(포인트 상점 설정·시드 아바타 품목)은 바꾸면 시험 끝에 되돌린다.
 */
@Import(PointShopAdminHttpTestBase.ClockConfig.class)
abstract class PointShopAdminHttpTestBase extends Commerce04AdminTestBase {

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableTestClock pointShopTestClock() {
            return new MutableTestClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
        }
    }

    protected static final String BASE = "/api/admin/point-shop";

    @Autowired
    protected MutableTestClock clock;
    @Autowired
    protected PointService points;
    @Autowired
    protected PointShopCatalogService catalog;
    @Autowired
    protected PointShopPurchaseService purchases;
    @Autowired
    private PlatformTransactionManager transactions;

    protected TransactionTemplate tx;
    /** 시험 품목을 만들고 포인트를 준 운영자 */
    protected long setupOperator;

    private Integer savedRefundDays;
    private List<Map<String, Object>> savedAvatars;

    @BeforeEach
    void pointShopBaseSetUp() {
        tx = new TransactionTemplate(transactions);
        clock.set(now());
        setupOperator = operatorWith();
        savedRefundDays = jdbc.queryForObject("SELECT refund_days FROM point_shop_settings WHERE id = 1", Integer.class);
        savedAvatars = jdbc.queryForList("SELECT id, price_point, active, updated_by, updated_at FROM point_shop_items WHERE kind = 'PROFILE_AVATAR'");
    }

    @AfterEach
    void pointShopBaseTearDown() {
        jdbc.update("UPDATE point_shop_settings SET refund_days = ? WHERE id = 1", savedRefundDays);
        jdbc.batchUpdate("UPDATE point_shop_items SET price_point = ?, active = ?, updated_by = ?, updated_at = ? WHERE id = ?",
                savedAvatars.stream().map(row -> new Object[] {row.get("price_point"), row.get("active"), row.get("updated_by"), row.get("updated_at"), row.get("id")})
                        .toList());
    }

    // ── 회원·포인트 ─────────────────────────────────────────────────────────

    /** 이메일 인증을 마친 회원 + 적립 포인트 */
    protected long buyer(int grant) {
        return buyerAt(grant, clock.instant());
    }

    /** {@code at} 에 포인트를 받은 회원(지난 구매를 만들 때 — 묶음이 구매보다 먼저 생기게) */
    protected long buyerAt(int grant, Instant at) {
        long member = newMember();
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", member);
        if (grant > 0) {
            clock.set(at);
            try {
                tx.executeWithoutResult(s -> points.adjust(member, grant, "시험 지급", setupOperator));
            } finally {
                clock.set(now());
            }
        }
        return member;
    }

    protected long balance(long member) {
        List<Long> rows = jdbc.queryForList("SELECT balance FROM point_balances WHERE user_id = ?", Long.class, member);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    protected void assertInvariants(long... members) {
        assertThat(PointInvariants.violations(jdbc, members)).as("포인트 불변식(commerce.md 5-5)").isEmpty();
    }

    // ── 품목·구매 ───────────────────────────────────────────────────────────

    /** 시험마다 겹치지 않는 코드 */
    protected String code(String prefix) {
        return prefix + "-" + Long.toString(ids.nextId(), 36);
    }

    protected long colorItem(int price, Integer stock) {
        return create(new PointShopItemSaveRequest(code("c"), "NICKNAME_COLOR", "PERMANENT", "시험 색", null, "시험 품목이에요.", null,
                "#E11D48", null, null, price, null, stock, null, null, true, 0));
    }

    protected long ticketItem(int price) {
        return create(new PointShopItemSaveRequest(code("t"), "NICKNAME_CHANGE_TICKET", "CONSUMABLE", "시험 변경권", null, "시험 품목이에요.",
                null, null, null, null, price, null, null, null, null, true, 0));
    }

    protected long create(PointShopItemSaveRequest request) {
        return Long.parseLong(tx.execute(s -> catalog.create(request, setupOperator, "127.0.0.1")).id());
    }

    protected PointShopPurchaseResultView buy(long member, long itemId) {
        return purchases.purchase(new PointShopBuyer(member, true), itemId, true);
    }

    protected long avatarItem(String avatarId) {
        return jdbc.queryForObject("SELECT id FROM point_shop_items WHERE avatar_id = ?", Long.class, avatarId);
    }

    protected Integer stock(long itemId) {
        return jdbc.queryForObject("SELECT stock FROM point_shop_items WHERE id = ?", Integer.class, itemId);
    }

    /** {@code at} 에 산다(셀프 환불 기한이 지난 구매를 만든다) — 시계는 지금으로 돌아온다 */
    protected PointShopPurchaseResultView buyAt(long member, long itemId, Instant at) {
        clock.set(at);
        try {
            return buy(member, itemId);
        } finally {
            clock.set(now());
        }
    }

    protected static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    protected int count(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    /** 이 행위·대상의 가장 최근 감사 한 줄 */
    protected Map<String, Object> lastAudit(String action, String targetId) {
        return jdbc.queryForMap("SELECT actor_id, result, target_type, target_id, ip, retention_class, detail FROM audit_logs WHERE action = ? AND target_id = ? "
                + "ORDER BY occurred_at DESC, id DESC LIMIT 1", action, targetId);
    }
}
