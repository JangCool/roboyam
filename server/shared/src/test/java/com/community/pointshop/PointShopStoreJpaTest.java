package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopRefundRequestStatus;
import com.community.pointshop.repository.master.PointShopPurchaseStore;
import com.community.pointshop.repository.master.PointShopPurchaseStore.AdminQuery;
import com.community.pointshop.repository.master.PointShopPurchaseStore.RequestRow;
import com.community.operator.repository.master.OperatorMemberAccountStore;
import com.community.pointshop.repository.master.PointShopMemberStore;
import com.community.pointshop.repository.master.UserItemStore;
import com.community.testsupport.JpaTestContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * 포인트 상점 저장소를 JPA·QueryDSL 로 옮긴 뒤에도 JDBC 때와 같은지(2026-11-07 3단계) — 구매마다 가장 최근 신청({@code DISTINCT ON} → {@code max(id) … GROUP BY}),
 * 관리자 목록의 신청 상태 거르기·회원번호 찾기, 넣기의 유일 위반은 {@link DuplicateKeyException}, 잠그는 읽기가 전환 전과 같은 {@code FOR UPDATE} 로 행을 잠그는지(다른 연결의
 * {@code FOR KEY SHARE NOWAIT} — FK 확인과 같은 세기 — 까지 막힌다).
 * 실제 PostgreSQL.
 */
class PointShopStoreJpaTest {

    private static PointShopTestKit kit;
    private static PointShopPurchaseStore store;

    @BeforeAll
    static void setUp() {
        kit = new PointShopTestKit();
        store = JpaTestContext.create(PointShopPurchaseStore.class);
    }

    @Test
    void 구매마다_가장_최근_신청을_고른다_거절_뒤_새_신청() {
        long item = kit.color(100, "#123456");
        long member = kit.newMember();
        kit.grant(member, 1000);
        long userItem = kit.buyItem(member, item);
        long purchase = kit.purchaseOf(userItem);
        long other = kit.purchaseOf(kit.buyItem(member, kit.color(100, "#654321")));

        long first = kit.ids.nextId();
        long second = kit.ids.nextId();
        kit.inTx(() -> {
            store.insertRequest(first, purchase, userItem, member, kit.clock.instant());
            return store.decide(first, PointShopRefundRequestStatus.REJECTED, kit.operator, "시험 거절", kit.clock.instant());
        });
        kit.inTx(() -> {
            store.insertRequest(second, purchase, userItem, member, kit.clock.instant());
            return null;
        });

        Map<Long, RequestRow> latest = store.latestRequests(List.of(purchase, other));
        assertThat(latest).containsOnlyKeys(purchase);
        assertThat(latest.get(purchase).id()).isEqualTo(second);
        assertThat(latest.get(purchase).status()).isEqualTo(PointShopRefundRequestStatus.PENDING);
        assertThat(store.latestRequest(purchase).orElseThrow().id()).isEqualTo(second);
        assertThat(store.latestRequest(other)).isEmpty();

        AdminQuery pending = new AdminQuery(null, null, null, PointShopItemKind.NICKNAME_COLOR, Long.toString(member), 0, 20, PointShopRefundRequestStatus.PENDING);
        assertThat(store.adminPage(pending)).extracting(row -> row.purchase().id()).as("대기 신청이 있는 구매만 — 회원번호로 찾기").containsExactly(purchase);
        assertThat(store.adminCount(pending)).isEqualTo(1);
        AdminQuery all = new AdminQuery(null, null, null, null, Long.toString(member), 0, 20, null);
        assertThat(store.adminPage(all)).extracting(row -> row.purchase().id()).as("최신 구매가 위").containsExactly(other, purchase);
        assertThat(store.adminCount(all)).isEqualTo(2);

        assertThatThrownBy(() -> kit.inTx(() -> {
            store.insertRequest(kit.ids.nextId(), purchase, userItem, member, kit.clock.instant());
            return null;
        })).as("같은 구매의 두 번째 대기 신청 — 유일 위반은 JDBC 때처럼 DuplicateKeyException").isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 잠그는_읽기는_그_행을_잠근다_다른_연결의_NOWAIT_가_막힌다() throws Exception {
        UserItemStore userItems = JpaTestContext.create(UserItemStore.class);
        PointShopMemberStore members = JpaTestContext.create(PointShopMemberStore.class);
        OperatorMemberAccountStore accounts = JpaTestContext.create(OperatorMemberAccountStore.class);
        long item = kit.color(100, "#0A0B0C");
        long member = kit.newMember();
        kit.grant(member, 1000);
        long userItem = kit.buyItem(member, item);
        long purchase = kit.purchaseOf(userItem);
        long request = kit.ids.nextId();
        kit.inTx(() -> {
            store.insertRequest(request, purchase, userItem, member, kit.clock.instant());
            return null;
        });

        assertLocked(() -> store.findForUpdate(purchase), "point_shop_purchases", "id", purchase);
        assertLocked(() -> store.findRequestForUpdate(request), "point_shop_refund_requests", "id", request);
        assertLocked(() -> userItems.findForUpdate(userItem), "user_items", "id", userItem);
        assertLocked(() -> userItems.byPurchaseForUpdate(purchase), "user_items", "id", userItem);
        assertLocked(() -> userItems.ownedPermanentForUpdate(member, item), "user_items", "id", userItem);
        assertLocked(() -> members.lockAvatar(member), "profiles", "user_id", member);
        assertLocked(() -> kit.itemStore.findForUpdate(item), "point_shop_items", "id", item);
        assertLocked(kit.itemStore::lockRefundDays, "point_shop_settings", "id", 1);
        assertLocked(() -> accounts.lockUser(member), "users", "id", member);
    }

    /** {@code lock} 을 트랜잭션 안에서 부르고 잡고 있는 동안, 다른 연결에서 그 행을 {@code FOR KEY SHARE NOWAIT} 로 잡으면 막혀야 한다(55P03 — FOR UPDATE 세기) */
    private static void assertLocked(Runnable lock, String table, String keyColumn, long key) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> kit.inTx(() -> {
            lock.run();
            locked.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        try {
            assertThat(locked.await(30, TimeUnit.SECONDS)).as(table + " 잠금 트랜잭션이 돌았다").isTrue();
            try (Connection connection = kit.dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + table + " WHERE " + keyColumn + " = ? FOR KEY SHARE NOWAIT")) {
                connection.setAutoCommit(false);
                statement.setLong(1, key);
                assertThatThrownBy(statement::executeQuery).as(table + " 행이 잠겨 있다").isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("55P03"));
                connection.rollback();
            }
        } finally {
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
        }
    }
}
