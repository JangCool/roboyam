package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.repository.master.GroupBuyProductStore;
import com.community.groupbuy.repository.master.GroupBuySettingsStore;
import com.community.testsupport.RowLockProbe;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 공구 저장소의 행 잠금 세기(commerce.md 18절 — 실제 PostgreSQL). 기본은 {@code PESSIMISTIC_WRITE}({@code FOR NO KEY UPDATE} — 같은 행 쓰기 잠금끼리 막고, 그 행을 FK 로
 * 가리키는 INSERT 의 확인 {@code FOR KEY SHARE} 는 막지 않는다). 상품 행만 네이티브 {@code FOR UPDATE}(공구 등록이 상품 행을 잠그지 않고 공구 줄을 넣는다 — 구성 바꾸기가 그
 * INSERT 를 기다려야 결과가 같다). 환불 워커의 {@code SKIP LOCKED} 는 쓰기 잠금 중인 주문만 건너뛴다.
 */
class GroupBuyLockStrengthTest {

    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    private static boolean keyShareBlocked(String table, long id) {
        return RowLockProbe.keyShareBlocked(kit.dataSource, table, "id = ?", id);
    }

    private static boolean noKeyUpdateBlocked(String table, long id) {
        return RowLockProbe.noKeyUpdateBlocked(kit.dataSource, table, "id = ?", id);
    }

    @Test
    void 공구_주문_설정_행은_FOR_NO_KEY_UPDATE_상품_행은_FOR_UPDATE_다() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        JoinResult paid = kit.paidOrder(kit.newMember(), gb, 1);
        long product = kit.productOf(gb);
        GroupBuyProductStore products = kit.spring.getBean(GroupBuyProductStore.class);
        GroupBuySettingsStore settings = kit.spring.getBean(GroupBuySettingsStore.class);
        kit.tx.executeWithoutResult(status -> {
            assertThat(kit.groupStore.lock(gb)).isPresent();
            assertThat(noKeyUpdateBlocked("group_buys", gb)).as("공구 행 — 쓰기 잠금끼리 막힌다").isTrue();
            assertThat(keyShareBlocked("group_buys", gb)).as("공구 행 — 주문 INSERT 의 FK 확인은 막지 않는다(선점 UPDATE 가 막힌다)").isFalse();
            assertThat(noKeyUpdateBlocked("group_buy_products", product)).as("상품 행은 아직(공구 잠금은 공구 행만)").isFalse();
            assertThat(products.lock(product)).isPresent();
            assertThat(keyShareBlocked("group_buy_products", product)).as("상품 행 — 공구 등록의 FK 확인까지 기다린다(FOR UPDATE)").isTrue();
            assertThat(kit.participationStore.lock(paid.participationId())).isPresent();
            assertThat(noKeyUpdateBlocked("group_buy_participations", paid.participationId())).as("주문 행").isTrue();
            assertThat(keyShareBlocked("group_buy_participations", paid.participationId())).as("주문 행 — FK 확인은 막지 않는다").isFalse();
            settings.lock();
            assertThat(noKeyUpdateBlocked("group_buy_settings", 1)).as("설정 행").isTrue();
        });
        assertThat(noKeyUpdateBlocked("group_buys", gb)).as("끝나면 풀린다").isFalse();
        assertThat(keyShareBlocked("group_buy_products", product)).as("끝나면 풀린다").isFalse();
    }

    @Test
    void 환불_워커의_SKIP_LOCKED_집기는_쓰기_잠금_중인_주문을_건너뛰고_FK_로_가리키며_넣는_중인_주문은_잡는다() throws SQLException {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        long participation = kit.paidOrder(kit.newMember(), gb, 1).participationId();
        assertThat(lockSkipLockedWhileOtherHolds(participation, "NO KEY UPDATE")).as("다른 트랜잭션이 주문 행을 쓰기 잠금 — 건너뛴다").isEmpty();
        assertThat(lockSkipLockedWhileOtherHolds(participation, "KEY SHARE"))
                .as("다른 트랜잭션이 그 주문을 FK 로 가리키는 줄(배송지 열람 기록 등)을 넣는 중 — 환불 계산에 쓰지 않는 줄이라 잡는다").isPresent();
        kit.tx.executeWithoutResult(status -> {
            assertThat(kit.participationStore.lockSkipLocked(participation)).as("놓이면 잡는다").isPresent();
            assertThat(noKeyUpdateBlocked("group_buy_participations", participation)).isTrue();
        });
    }

    /** 다른 연결이 그 주문 행을 {@code FOR <mode>} 로 잡고 있는 동안 환불 워커의 집기 */
    private static Optional<?> lockSkipLockedWhileOtherHolds(long participation, String mode) throws SQLException {
        try (Connection holder = kit.dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement statement = holder.prepareStatement("SELECT 1 FROM group_buy_participations WHERE id = ? FOR " + mode)) {
                statement.setLong(1, participation);
                statement.executeQuery().close();
            }
            try {
                return kit.tx.execute(status -> kit.participationStore.lockSkipLocked(participation));
            } finally {
                holder.rollback();
            }
        }
    }
}
