package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.groupbuy.service.GroupBuySavepoint;
import com.community.payment.dto.CancelKind;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.repository.master.PaymentEventStore;
import com.community.payment.repository.master.PaymentOrderStore.NewOrder;
import com.community.payment.repository.master.PaymentOrderStore.PaidFields;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 결제 저장소의 JPA·QueryDSL 전환(3단계 1차)이 지켜야 하는 것 — 예전 JDBC 저장소와 같은 결과를 실제 PostgreSQL 에서 확인한다.
 *
 * <ul>
 *   <li>넣기는 미뤄 두지 않는다: 결제 확정 트랜잭션은 처리기({@code onPaid})를 JDBC 세이브포인트({@link GroupBuySavepoint})로 감싸므로 그 전의 결제 쓰기가 영속성 문맥에 남아 있으면
 *       세이브포인트 안의 JPA 질의가 그것을 내보냈다가 되돌리기에 휩쓸린다.</li>
 *   <li>잠금({@code lockById})은 결제 주문·취소 행을 전환 전과 같은 {@code FOR UPDATE} 로 잡는다(다른 연결의 {@code FOR KEY SHARE NOWAIT} — 그 행을 FK 로 가리키는 INSERT 의
 *       확인 — 까지 막힌다).</li>
 *   <li>열린 READY 닫기({@code expireOpenReady})는 예전 {@code UPDATE … RETURNING id} 처럼 닫은 줄만 돌려준다.</li>
 * </ul>
 */
class PaymentStoreJpaTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static PaymentTestKit kit;
    private static PaymentEventStore events;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
        events = kit.spring.getBean(PaymentEventStore.class);
    }

    @BeforeEach
    void epoch() {
        kit.newEpoch();
    }

    private static String paymentId() {
        StringBuilder id = new StringBuilder("pay_");
        for (int i = 0; i < 32; i++) {
            id.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        return id.toString();
    }

    private static long insertReady(long orderRefId) {
        long id = kit.ids.nextId();
        Instant now = kit.now();
        kit.orderStore.insert(new NewOrder(id, paymentId(), kit.newMember(), PaymentOrderType.PAYMENT_TEST, orderRefId, "시험 결제", 1_000, now.plus(Duration.ofMinutes(5)),
                now));
        return id;
    }

    private static int count(String sql, Object... args) {
        Integer n = kit.jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void 결제_쓰기는_바로_DB_에_닿아_세이브포인트_되돌리기에_휩쓸리지_않는다() {
        // 세이브포인트를 잡기 전에 미뤄 둔 JPA 쓰기를 내보낸다(공구 3단계에서 GroupBuySavepoint 가 EntityManager 를 받게 됐다)
        GroupBuySavepoint savepoint = new GroupBuySavepoint(kit.dataSource, com.community.testsupport.JpaTestContext.entityManager());
        long[] orderId = new long[1];
        kit.tx.executeWithoutResult(status -> {
            orderId[0] = insertReady(kit.ids.nextId());
            events.insert(kit.ids.nextId(), orderId[0], PaymentEventStore.Kind.CREATED, Map.of("amount", 1_000), kit.now());
            kit.orderStore.markPaid(orderId[0], new PaidFields(1_000, kit.now(), "PaymentMethodCard", "FAKE", "tx-1", null), null, ConfirmedVia.COMPLETE_API, kit.now());
            events.insert(kit.ids.nextId(), orderId[0], PaymentEventStore.Kind.PAID, Map.of("via", "COMPLETE_API"), kit.now());
            // 같은 트랜잭션의 JDBC 로 보인다 = 영속성 문맥에 미뤄 둔 쓰기가 없다
            assertThat(count("SELECT count(*) FROM payment_orders WHERE id = ?", orderId[0])).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM payment_events WHERE payment_order_id = ?", orderId[0])).isEqualTo(2);
            assertThatThrownBy(() -> savepoint.run(() -> {
                kit.orderStore.findById(orderId[0]); // 세이브포인트 안의 JPA 질의(자동 내보내기)
                kit.jdbc.update("UPDATE payment_orders SET order_name = '되돌려질 이름' WHERE id = ?", orderId[0]);
                throw new IllegalStateException("도메인 거절");
            })).isInstanceOf(IllegalStateException.class);
            kit.orderStore.markAnomaly(orderId[0], PaymentAnomaly.DOMAIN_REJECTED, kit.now());
        });
        assertThat(kit.orderStore.findById(orderId[0]).orElseThrow()).satisfies(order -> {
            assertThat(order.status()).isEqualTo(PaymentStatus.PAID);
            assertThat(order.anomaly()).isEqualTo(PaymentAnomaly.DOMAIN_REJECTED);
            assertThat(order.orderName()).as("세이브포인트 안의 쓰기만 되돌려졌다").isEqualTo("시험 결제");
        });
        assertThat(count("SELECT count(*) FROM payment_events WHERE payment_order_id = ?", orderId[0])).isEqualTo(2);
    }

    /** 다른 연결에서 그 표의 행을 기다리지 않고(NOWAIT) 키 나눔 잠금(FK 확인과 같은 세기) 해 본다 — {@code FOR UPDATE} 로 잠겨 있으면 false */
    private static boolean lockableNow(String table, long id) {
        try (Connection connection = kit.dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM " + table + " WHERE id = ? FOR KEY SHARE NOWAIT")) {
                statement.setLong(1, id);
                statement.executeQuery().close();
                return true;
            } catch (SQLException e) {
                if ("55P03".equals(e.getSQLState())) {
                    return false;
                }
                throw new IllegalStateException(e);
            } finally {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 결제_주문과_취소_행_잠금은_트랜잭션이_끝날_때까지_다른_연결을_막는다() {
        long orderId = kit.tx.execute(status -> insertReady(kit.ids.nextId()));
        long cancelId = kit.ids.nextId();
        assertThat(kit.tx.<Boolean>execute(status -> kit.cancelStore.insert(cancelId, orderId, 500, "시험", CancelKind.REFUND, "R:" + cancelId, null, kit.now())))
                .isTrue();
        kit.tx.executeWithoutResult(status -> {
            assertThat(kit.orderStore.lockById(orderId)).isPresent();
            assertThat(lockableNow("payment_orders", orderId)).as("결제 주문 행이 잠겼다").isFalse();
            assertThat(lockableNow("payment_cancels", cancelId)).as("취소 행은 아직").isTrue();
            assertThat(kit.cancelStore.lockById(cancelId)).isPresent();
            assertThat(lockableNow("payment_cancels", cancelId)).as("취소 행이 잠겼다").isFalse();
        });
        assertThat(lockableNow("payment_orders", orderId)).as("끝나면 풀린다").isTrue();
        assertThat(lockableNow("payment_cancels", cancelId)).isTrue();
        assertThat(kit.tx.<java.util.Optional<?>>execute(status -> kit.orderStore.lockById(kit.ids.nextId()))).as("없는 줄").isEmpty();
    }

    @Test
    void 열린_READY_닫기는_닫은_줄만_돌려주고_다른_주문_다른_상태는_건드리지_않는다() {
        long ref = kit.ids.nextId();
        long first = kit.tx.execute(status -> insertReady(ref));
        long second = kit.tx.execute(status -> insertReady(ref));
        long paid = kit.tx.execute(status -> {
            long id = insertReady(ref);
            kit.orderStore.expireReady(id, kit.now());
            return id;
        });
        long other = kit.tx.execute(status -> insertReady(kit.ids.nextId()));

        List<Long> closed = kit.tx.execute(status -> kit.orderStore.expireOpenReady(PaymentOrderType.PAYMENT_TEST, ref, kit.now()));
        assertThat(closed).containsExactlyInAnyOrder(first, second);
        assertThat(kit.orderStore.findById(first).orElseThrow().status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(kit.orderStore.findById(second).orElseThrow().status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(kit.orderStore.findById(paid).orElseThrow().status()).as("이미 닫힌 줄은 돌려주지 않는다").isEqualTo(PaymentStatus.EXPIRED);
        assertThat(kit.orderStore.findById(other).orElseThrow().status()).isEqualTo(PaymentStatus.READY);
        assertThat(kit.tx.<List<Long>>execute(status -> kit.orderStore.expireOpenReady(PaymentOrderType.PAYMENT_TEST, ref, kit.now()))).as("다시 닫으면 없다").isEmpty();
    }
}
