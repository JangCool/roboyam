package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.dto.StockChange;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.QtyMovementKind;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 모집 뒤 수량 조정 — 추가 입고·줄이기(계약 결정 17·27·48·80·84, G2·G3·G4·G5·G29).
 */
class GroupBuyStockTest {

    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    private String policy() {
        return kit.policy(3_000, null, 3_000, "ONE_WAY");
    }

    @Test
    void G2_마지막_하나가_확정된_직후_워커_전에_추가_입고하면_이미_CLOSING_이라_거절() {
        long gb = kit.spec(policy()).qty(0, 2).open();
        kit.paidOrder(kit.newMember(), gb, 2);
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.CLOSING);
        assertThatThrownBy(() -> kit.admin.addStock(gb, null, 5, "추가 입고", kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.NOT_RECRUITING));
        assertThat(kit.group(gb).maxQty()).isEqualTo(2);
        kit.assertInvariants(gb);
    }

    @Test
    void G3_잠시_품절에서_추가_입고한_뒤_선점이_만료되면_추가분과_풀린_수량을_모두_판다() {
        long gb = kit.spec(policy()).qty(0, 2).open();
        JoinResult holding = kit.order(kit.newMember(), gb, 2); // 확정 0 + 선점 2 = 최대 → 잠시 품절
        assertThat(kit.quotes.availability(gb).temporarilyUnavailable()).isTrue();

        StockChange change = kit.admin.addStock(gb, null, 3, "입고", kit.operator, "ip");
        assertThat(change.maxQty()).isEqualTo(5);
        assertThat(change.addedQty()).isEqualTo(3);
        kit.advance(Duration.ofMinutes(6));
        kit.holds.expireOverdue(1000);
        assertThat(kit.participation(holding.participationId()).status().name()).isEqualTo("EXPIRED");

        kit.paidOrder(kit.newMember(), gb, 5);
        assertThat(kit.group(gb).currentQty()).isEqualTo(5);
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.CLOSING);
        kit.assertInvariants(gb);
    }

    @Test
    void G4_줄이기_하한은_확정_더하기_선점과_성사형_최소_수량이고_줄여서_다_팔리면_즉시_CLOSING() {
        long gb = kit.spec(policy()).qty(3, 10).open();
        kit.paidOrder(kit.newMember(), gb, 2);
        kit.order(kit.newMember(), gb, 2); // 선점 2
        assertThatThrownBy(() -> kit.admin.reduceStock(gb, null, 7, "불량", kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.QTY_BELOW_FLOOR);
                    assertThat(e.getExtras()).containsEntry("floor", 4);
                });
        StockChange reduced = kit.admin.reduceStock(gb, null, 6, "입고 부족", kit.operator, "ip");
        assertThat(reduced.maxQty()).isEqualTo(4);
        assertThat(reduced.closed()).isFalse();

        long firstCome = kit.spec(policy()).qty(0, 10).open();
        kit.paidOrder(kit.newMember(), firstCome, 4);
        StockChange closed = kit.admin.reduceStock(firstCome, null, 6, "품절", kit.operator, "ip");
        assertThat(closed.closed()).isTrue();
        assertThat(kit.group(firstCome).status()).isEqualTo(GroupBuyStatus.CLOSING);
        assertThat(kit.group(firstCome).closeReason()).isEqualTo("SOLD_OUT");
        kit.assertInvariants(gb, firstCome);
    }

    @Test
    void 성사형_최소_수량_아래로는_줄이지_못한다() {
        long gb = kit.spec(policy()).qty(5, 10).open();
        assertThatThrownBy(() -> kit.admin.reduceStock(gb, null, 6, "불량", kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getExtras()).containsEntry("floor", 5));
    }

    @Test
    void G5_G29_옵션_추가_입고와_줄이기는_공구_최대도_같은_수만큼_움직이고_식이_맞는다() {
        long gb = kit.spec(policy()).qty(0, 10).option("빨강", 0, 3).option("파랑", 0, 3).option("자유", 0, null).open();
        long red = kit.skuId(gb, "빨강");
        long free = kit.skuId(gb, "자유");
        kit.paidOrder(kit.newMember(), gb, lines(red, 3));
        assertThat(kit.quotes.availability(gb).skus().stream().filter(o -> o.skuId() == red).findFirst().orElseThrow().soldOut())
                .as("옵션 다 팔림 — 공구는 모집 중").isTrue();

        StockChange added = kit.admin.addStock(gb, red, 2, "빨강 입고", kit.operator, "ip");
        assertThat(added.skuMaxQty()).isEqualTo(5);
        assertThat(added.maxQty()).isEqualTo(12);
        StockChange reduced = kit.admin.reduceStock(gb, kit.skuId(gb, "파랑"), 1, "파랑 불량", kit.operator, "ip");
        assertThat(reduced.skuMaxQty()).isEqualTo(2);
        assertThat(reduced.maxQty()).isEqualTo(11);

        assertThatThrownBy(() -> kit.admin.addStock(gb, free, 1, "자유 입고", kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.SKU_UNLIMITED));
        assertThatThrownBy(() -> kit.admin.reduceStock(gb, red, 3, "하한 아래", kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.QTY_BELOW_FLOOR);
                    assertThat(e.getExtras()).containsEntry("floor", 3);
                });
        assertThat(kit.group(gb).maxQty()).as("실패한 옵션 줄이기는 공구 최대도 되돌린다").isEqualTo(11);

        var g = kit.group(gb);
        assertThat(g.initialMaxQty()).isEqualTo(10);
        assertThat(g.addedQty()).isEqualTo(2);
        assertThat(kit.quotes.availability(gb).addedQty()).as("「추가 입고 N개」 = max(0, 최대 − 처음)").isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM group_buy_revisions WHERE group_buy_id = ? AND change_kind IN ('STOCK_ADD', 'STOCK_REDUCE')", gb)).isEqualTo(2);
        assertThat(kit.audits).anySatisfy(a -> assertThat(a.action()).isEqualTo(AuditActions.GROUPBUY_STOCK_ADD));
        assertThat(kit.audits).anySatisfy(a -> assertThat(a.action()).isEqualTo(AuditActions.GROUPBUY_STOCK_REDUCE));
        assertThat(kit.eventsOf(GroupBuyEvents.QtyReleased.class)).anySatisfy(e -> assertThat(e.kind()).isEqualTo(QtyMovementKind.ADD));
        kit.assertInvariants(gb);
    }

    @Test
    void 사유가_없거나_수량이_0이면_400() {
        long gb = kit.spec(policy()).open();
        assertThatThrownBy(() -> kit.admin.addStock(gb, null, 1, " ", kit.operator, "ip")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> kit.admin.reduceStock(gb, null, 0, "사유", kit.operator, "ip")).isInstanceOf(ValidationException.class);
    }

    @Test
    void G5_여러_줄_동시_선점과_결제_추가_입고가_섞여도_교착_없이_수량이_맞는다() throws Exception {
        long gb = kit.spec(policy()).qty(0, 40).option("A", 0, 20).option("B", 0, 20).option("C", 0, null).open();
        long a = kit.skuId(gb, "A");
        long b = kit.skuId(gb, "B");
        long c = kit.skuId(gb, "C");
        List<java.util.concurrent.Callable<Object>> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            long member = kit.newMember();
            List<com.community.groupbuy.dto.OrderLine> order = i % 2 == 0 ? lines(b, 2, a, 1, c, 1) : lines(c, 1, a, 2, b, 1);
            tasks.add(() -> kit.pay(kit.order(member, gb, order)));
        }
        tasks.add(() -> kit.admin.addStock(gb, a, 2, "동시 입고", kit.operator, "ip"));
        tasks.add(() -> kit.admin.reduceStock(gb, null, 1, "동시 줄이기", kit.operator, "ip"));
        List<Object> results = GroupBuyTestKit.runTogether(tasks);
        assertThat(results).noneMatch(r -> r instanceof RuntimeException && !(r instanceof GroupBuyException));
        kit.assertInvariants(gb);
    }
}
