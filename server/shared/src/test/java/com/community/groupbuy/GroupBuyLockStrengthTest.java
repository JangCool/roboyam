package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.repository.master.GroupBuyProductStore;
import com.community.groupbuy.repository.master.GroupBuySettingsStore;
import com.community.testsupport.RowLockProbe;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 공구 저장소의 행 잠금 세기 — 전환 전 네이티브와 같은 {@code FOR UPDATE}(그 행을 FK 로 가리키는 INSERT 의 확인 {@code FOR KEY SHARE} 까지 막는다)이고, 환불 워커의
 * {@code SKIP LOCKED} 집기는 다른 트랜잭션이 그 주문을 FK 로 가리키며 넣는 중이면 건너뛴다(commerce.md 18절 — 실제 PostgreSQL).
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

    @Test
    void 공구_주문_상품_설정_행_잠금은_FOR_UPDATE_다() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        JoinResult paid = kit.paidOrder(kit.newMember(), gb, 1);
        long product = kit.productOf(gb);
        GroupBuyProductStore products = kit.spring.getBean(GroupBuyProductStore.class);
        GroupBuySettingsStore settings = kit.spring.getBean(GroupBuySettingsStore.class);
        kit.tx.executeWithoutResult(status -> {
            assertThat(kit.groupStore.lock(gb)).isPresent();
            assertThat(keyShareBlocked("group_buys", gb)).as("공구 행").isTrue();
            assertThat(keyShareBlocked("group_buy_products", product)).as("상품 행은 아직(공구 잠금은 공구 행만)").isFalse();
            assertThat(products.lock(product)).isPresent();
            assertThat(keyShareBlocked("group_buy_products", product)).as("상품 행").isTrue();
            assertThat(kit.participationStore.lock(paid.participationId())).isPresent();
            assertThat(keyShareBlocked("group_buy_participations", paid.participationId())).as("주문 행").isTrue();
            settings.lock();
            assertThat(keyShareBlocked("group_buy_settings", 1)).as("설정 행").isTrue();
        });
        assertThat(keyShareBlocked("group_buys", gb)).as("끝나면 풀린다").isFalse();
    }

    @Test
    void 환불_워커의_SKIP_LOCKED_집기는_FK_로_가리키며_넣는_중인_주문을_건너뛴다() throws SQLException {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
        long participation = kit.paidOrder(kit.newMember(), gb, 1).participationId();
        try (Connection holder = kit.dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement keyShare = holder.prepareStatement("SELECT 1 FROM group_buy_participations WHERE id = ? FOR KEY SHARE")) {
                keyShare.setLong(1, participation); // 다른 트랜잭션이 그 주문을 FK 로 가리키는 줄(이력·요청·환불…)을 넣는 중인 것과 같은 잠금
                keyShare.executeQuery().close();
            }
            try {
                assertThat(kit.tx.<java.util.Optional<?>>execute(status -> kit.participationStore.lockSkipLocked(participation))).as("전환 전과 같이 건너뛴다(FOR UPDATE SKIP LOCKED)").isEmpty();
            } finally {
                holder.rollback();
            }
        }
        kit.tx.executeWithoutResult(status -> {
            assertThat(kit.participationStore.lockSkipLocked(participation)).as("놓이면 잡는다").isPresent();
            assertThat(keyShareBlocked("group_buy_participations", participation)).isTrue();
        });
    }
}
