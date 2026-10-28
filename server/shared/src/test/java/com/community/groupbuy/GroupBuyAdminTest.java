package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.CancelSummary;
import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.dto.RoundCreated;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.service.ShippingPolicyService;
import com.community.payment.dto.PaymentStatus;
import com.community.zconfiguration.exception.ValidationException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 공구 운영(등록·수정·모집 시작·중단·추가 차수)과 배송비 정책 — commerce.md 10-2, 18절 P16e, 계약 결정 17·29·31·51·58·80, G8·G9·G10(중단 일부)·G37.
 */
class GroupBuyAdminTest {

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
    void G37_마감_후_발주_공구는_발송_예정일이_없으면_저장_거절_즉시_발송은_선착순만() {
        GroupBuyDraft base = kit.spec(policy()).qty(3, 10).draft();
        GroupBuyDraft noPlan = new GroupBuyDraft(base.title(), base.productName(), base.summary(), base.spec(), base.description(), null, null, null, base.groupPrice(),
                base.shippingPolicyCode(), 3, 10, null, base.startAt(), base.endAt(), "DEMAND", "AFTER_CLOSE", null, null, null, null, false, List.of(), List.of());
        assertThatThrownBy(() -> kit.admin.create(noPlan, kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("shipPlannedAt"));
        GroupBuyDraft immediateDemand = new GroupBuyDraft(base.title(), base.productName(), null, null, null, null, null, null, base.groupPrice(), base.shippingPolicyCode(),
                3, 10, null, base.startAt(), base.endAt(), "DEMAND", "IMMEDIATE", null, null, null, null, false, List.of(), List.of());
        assertThatThrownBy(() -> kit.admin.create(immediateDemand, kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("fulfillment"));
        GroupBuyDraft normalPriceWithoutBasis = new GroupBuyDraft(base.title(), base.productName(), null, null, null, 30_000L, null, null, base.groupPrice(),
                base.shippingPolicyCode(), 0, 10, null, base.startAt(), base.endAt(), "DEMAND", "IMMEDIATE", null, null, null, null, false, List.of(), List.of());
        assertThatThrownBy(() -> kit.admin.create(normalPriceWithoutBasis, kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("normalPriceBasis"));
    }

    @Test
    void 즉시_발송의_발송_영업일은_비면_공구_설정_기본값() {
        long gb = kit.spec(policy()).create();
        assertThat(kit.group(gb).shipWithinBusinessDays()).isEqualTo(3);
    }

    @Test
    void 모집_시작은_그_시각의_배송비를_복사하고_처음_수량_수정_이력_1_감사() {
        String policy = kit.policy(3_000, 50_000L, 2_500, "ROUND_TRIP");
        long gb = kit.spec(policy).option("A", 0, 5).open();
        var g = kit.group(gb);
        assertThat(g.status()).isEqualTo(GroupBuyStatus.RECRUITING);
        assertThat(g.shippingFee()).isEqualTo(3_000);
        assertThat(g.freeShippingOver()).isEqualTo(50_000);
        assertThat(g.returnShippingFee()).isEqualTo(2_500);
        assertThat(g.returnFeeMode()).isEqualTo("ROUND_TRIP");
        assertThat(g.initialMaxQty()).isEqualTo(100);
        assertThat(kit.options(gb).get(0).initialMaxQty()).isEqualTo(5);
        assertThat(kit.count("SELECT count(*) FROM group_buy_revisions WHERE group_buy_id = ? AND revision_no = 1 AND change_kind = 'START'", gb)).isEqualTo(1);
        assertThat(kit.audits).extracting(a -> a.action()).contains(AuditActions.GROUPBUY_CREATE, AuditActions.GROUPBUY_START);
    }

    @Test
    void 기간이_설정_상한을_넘으면_모집_시작_거절_배송비_정책이_없으면_거절() {
        long demand = kit.spec(policy()).qty(2, 10).period(kit.now(), kit.now().plus(Duration.ofDays(15))).create();
        assertThatThrownBy(() -> kit.admin.publish(demand, kit.group(demand).version(), kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("endAt"));
        long firstCome = kit.spec(policy()).period(kit.now(), kit.now().plus(Duration.ofDays(15))).create();
        kit.admin.publish(firstCome, kit.group(firstCome).version(), kit.operator, "ip"); // 선착순 30일 안
        long noPolicy = kit.spec("NOPOLICY" + Long.toString(kit.ids.nextId(), 36).toUpperCase().substring(0, 6)).create();
        assertThatThrownBy(() -> kit.admin.publish(noPolicy, kit.group(noPolicy).version(), kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.SHIPPING_POLICY_NOT_FOUND));
    }

    @Test
    void 모집_뒤에는_설명_이미지만_고칠_수_있고_가격_기간_옵션은_LOCKED_FIELD() {
        GroupBuyTestKit.Spec spec = kit.spec(policy()).option("A", 0, 5);
        long gb = spec.open();
        GroupBuyDraft original = spec.draft();
        GroupBuyDraft priceChanged = new GroupBuyDraft(original.title(), original.productName(), original.summary(), original.spec(), original.description(), null, null,
                null, 30_000, original.shippingPolicyCode(), original.minQty(), original.maxQty(), null, original.startAt(), original.endAt(), original.extensionMode(),
                original.fulfillment(), null, null, null, null, false, original.options(), original.images());
        assertThatThrownBy(() -> kit.admin.update(gb, kit.group(gb).version(), priceChanged, kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.LOCKED_FIELD);
                    assertThat(e.getExtras()).containsEntry("field", "groupPrice");
                });
        GroupBuyDraft optionAdded = new GroupBuyDraft(original.title(), original.productName(), original.summary(), original.spec(), original.description(), null, null,
                null, original.groupPrice(), original.shippingPolicyCode(), original.minQty(), original.maxQty(), null, original.startAt(), original.endAt(),
                original.extensionMode(), original.fulfillment(), null, null, null, null, false,
                List.of(new GroupBuyDraft.OptionDraft("A", 0, 5), new GroupBuyDraft.OptionDraft("B", 0, 5)), original.images());
        assertThatThrownBy(() -> kit.admin.update(gb, kit.group(gb).version(), optionAdded, kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getExtras()).containsEntry("field", "options"));

        GroupBuyDraft described = new GroupBuyDraft(original.title(), original.productName(), "새 요약", original.spec(), "<p>새 설명</p>", null, null, null,
                original.groupPrice(), original.shippingPolicyCode(), original.minQty(), original.maxQty(), null, original.startAt(), original.endAt(),
                original.extensionMode(), original.fulfillment(), null, null, null, null, false, original.options(),
                List.of(new GroupBuyDraft.ImageRef("img/b.jpg", null)));
        int version = kit.group(gb).version();
        kit.admin.update(gb, version, described, kit.operator, "ip");
        var g = kit.group(gb);
        assertThat(g.description()).isEqualTo("<p>새 설명</p>");
        assertThat(g.revisionNo()).isEqualTo(2);
        assertThat(kit.count("SELECT count(*) FROM group_buy_revisions WHERE group_buy_id = ? AND revision_no = 2 AND change_kind = 'CONTENT' "
                + "AND images @> '[{\"imageKey\":\"img/b.jpg\"}]'", gb)).isEqualTo(1);
        assertThatThrownBy(() -> kit.admin.update(gb, version, described, kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.VERSION_CONFLICT));
    }

    @Test
    void 공개_전_DRAFT_는_모든_칸을_고칠_수_있다() {
        GroupBuyTestKit.Spec spec = kit.spec(policy());
        long gb = spec.create();
        spec.price(30_000).option("새 옵션", 1_000, 3);
        kit.admin.update(gb, kit.group(gb).version(), spec.draft(), kit.operator, "ip");
        assertThat(kit.group(gb).groupPrice()).isEqualTo(30_000);
        assertThat(kit.options(gb)).singleElement().satisfies(o -> assertThat(o.name()).isEqualTo("새 옵션"));
    }

    @Test
    void G8_추가_차수는_끝난_공구에서만_복사해_만들고_두_번_동시에_눌러도_하나_모집_시작에_이전_신청자에게_한_번() throws Exception {
        long root = kit.spec(policy()).qty(0, 2).option("A", 500, 2).open();
        assertThatThrownBy(() -> kit.admin.createRound(root, kit.now(), kit.now().plus(Duration.ofDays(1)), null, kit.operator, "ip"))
                .as("모집 중 원본").isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.ROUND_SOURCE_INVALID));
        kit.paidOrder(kit.newMember(), root, GroupBuyTestKit.lines(kit.optionId(root, "A"), 2));
        assertThat(kit.group(root).status()).isEqualTo(GroupBuyStatus.CLOSING);

        long watcher1 = kit.newMember();
        long watcher2 = kit.newMember();
        for (String kind : List.of("NEXT_ROUND", "RESTOCK")) {
            kit.jdbc.update("INSERT INTO group_buy_watches (id, group_buy_id, user_id, kind, created_at) VALUES (?, ?, ?, ?, now())", kit.ids.nextId(), root, watcher1, kind);
        }
        kit.jdbc.update("INSERT INTO group_buy_watches (id, group_buy_id, user_id, kind, created_at) VALUES (?, ?, ?, 'CLOSING_SOON', now())", kit.ids.nextId(), root,
                watcher2);

        Instant start = kit.now().plus(Duration.ofMinutes(5));
        Instant end = kit.now().plus(Duration.ofDays(2));
        List<Object> results = GroupBuyTestKit.runTogether(List.of(
                () -> kit.admin.createRound(root, start, end, null, kit.operator, "ip"),
                () -> kit.admin.createRound(root, start, end, null, kit.operator, "ip")));
        assertThat(results.stream().filter(RoundCreated.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(r -> r instanceof GroupBuyException e && e.getErrorCode().equals(GroupBuyException.ROUND_EXISTS))).hasSize(1);
        RoundCreated round = (RoundCreated) results.stream().filter(RoundCreated.class::isInstance).findFirst().orElseThrow();
        assertThat(round.roundNo()).isEqualTo(2);
        assertThat(round.roundRootId()).isEqualTo(root);
        assertThat(round.reviewWarning()).isTrue();

        var copy = kit.group(round.groupBuyId());
        var source = kit.group(root);
        assertThat(copy.status()).isEqualTo(GroupBuyStatus.DRAFT);
        assertThat(copy.title()).isEqualTo(source.title());
        assertThat(copy.description()).isEqualTo(source.description());
        assertThat(copy.groupPrice()).isEqualTo(source.groupPrice());
        assertThat(copy.fulfillment()).isEqualTo(source.fulfillment());
        assertThat(copy.extensionMode()).isEqualTo(source.extensionMode());
        assertThat(copy.startAt()).isEqualTo(start);
        assertThat(copy.shippingFee()).as("배송비는 모집 시작 때 정책에서").isNull();
        assertThat(kit.options(round.groupBuyId())).singleElement().satisfies(o -> {
            assertThat(o.name()).isEqualTo("A");
            assertThat(o.extraPrice()).isEqualTo(500);
        });
        assertThat(kit.groupStore.images(round.groupBuyId())).hasSize(1);
        assertThat(kit.audits).anySatisfy(a -> assertThat(a.action()).isEqualTo(AuditActions.GROUPBUY_ROUND_CREATE));

        assertThatThrownBy(() -> kit.admin.createRound(root, start, end, null, kit.operator, "ip"))
                .as("DRAFT 차수가 이미 있다").isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.ROUND_EXISTS));

        kit.admin.publish(round.groupBuyId(), copy.version(), kit.operator, "ip");
        kit.advance(Duration.ofMinutes(6));
        kit.runStateWorker();
        assertThat(kit.group(round.groupBuyId()).status()).isEqualTo(GroupBuyStatus.RECRUITING);
        assertThat(kit.eventsOf(GroupBuyEvents.RoundStarted.class)).filteredOn(e -> e.groupBuyId() == round.groupBuyId()).singleElement().satisfies(e -> {
            assertThat(e.roundNo()).isEqualTo(2);
            assertThat(e.recipients()).containsExactlyInAnyOrder(watcher1, watcher2);
        });
    }

    @Test
    void 중단된_공구에서는_추가_차수를_열지_않는다() {
        long gb = kit.spec(policy()).open();
        kit.admin.cancel(gb, "중단", kit.operator, "ip");
        assertThatThrownBy(() -> kit.admin.createRound(gb, kit.now(), kit.now().plus(Duration.ofDays(1)), null, kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.ROUND_SOURCE_INVALID));
    }

    @Test
    void 결정29_중단은_공구만_먼저_바꾸고_환불_워커가_발송_전_주문만_환불_결제_중은_닫고_발송된_주문은_그대로() {
        long gb = kit.spec(policy()).qty(0, 100).open();
        JoinResult paid = kit.paidOrder(kit.newMember(), gb, 2);
        JoinResult shipped = kit.paidOrder(kit.newMember(), gb, 1);
        kit.jdbc.update("UPDATE group_buy_participations SET status = 'SHIPPED' WHERE id = ?", shipped.participationId());
        JoinResult pending = kit.order(kit.newMember(), gb, 1);

        CancelSummary preview = kit.admin.cancelPreview(gb);
        assertThat(preview).isEqualTo(new CancelSummary(1, 53_000, 1, 1));
        CancelSummary summary = kit.admin.cancel(gb, "공급처 품절", kit.operator, "ip");
        assertThat(summary).isEqualTo(preview);
        var g = kit.group(gb);
        assertThat(g.status()).isEqualTo(GroupBuyStatus.CANCELED);
        assertThat(g.closeReason()).isEqualTo("CANCELED");
        assertThat(kit.participation(paid.participationId()).status()).as("① 은 공구만").isEqualTo(ParticipationStatus.CONFIRMED);
        assertThat(kit.audits).anySatisfy(a -> assertThat(a.action()).isEqualTo(AuditActions.GROUPBUY_CANCEL));

        kit.runRefundWorker();
        var refunded = kit.participation(paid.participationId());
        assertThat(refunded.status()).isEqualTo(ParticipationStatus.REFUNDED);
        assertThat(refunded.refundedAmount()).isEqualTo(53_000);
        assertThat(kit.payment(paid.checkout().paymentId()).status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(kit.participation(shipped.participationId()).status()).isEqualTo(ParticipationStatus.SHIPPED);
        assertThat(kit.participation(pending.participationId()).status()).isEqualTo(ParticipationStatus.EXPIRED);
        assertThat(kit.count("SELECT count(*) FROM group_buy_refunds WHERE participation_id = ? AND cause = 'CANCELED'", refunded.id())).isEqualTo(1);

        kit.runRefundWorker(); // 다시 돌려도 한 번
        assertThat(kit.count("SELECT count(*) FROM group_buy_refunds WHERE participation_id = ?", refunded.id())).isEqualTo(1);
        assertThatThrownBy(() -> kit.admin.cancel(gb, "두 번", kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.INVALID_STATE));
        // 발송된 주문(SHIPPED)은 불변식의 판매 수량에 남는다
        kit.assertInvariants(gb);
    }

    @Test
    void 조기_마감은_CLOSING_으로_closing_at_은_지금_종료_사유는_EARLY_CLOSED() {
        long gb = kit.spec(policy()).open();
        kit.admin.closeEarly(gb, "운영 판단", kit.operator, "ip");
        var g = kit.group(gb);
        assertThat(g.status()).isEqualTo(GroupBuyStatus.CLOSING);
        assertThat(g.closingAt()).isEqualTo(kit.now());
        assertThat(g.closeReason()).as("2b-1 사용자 결정 — 기간 끝(TIME_UP)과 나눈다").isEqualTo("EARLY_CLOSED");
        assertThat(kit.audits).anySatisfy(a -> assertThat(a.action()).isEqualTo(AuditActions.GROUPBUY_CLOSE_EARLY));
        assertThatThrownBy(() -> kit.admin.closeEarly(gb, "두 번", kit.operator, "ip")).isInstanceOf(GroupBuyException.class);
    }

    // ── 배송비 정책 ─────────────────────────────────────────────────────────

    @Test
    void P16e_배송비_인상을_예약하면_진행_중_공구는_끝까지_옛_값_예약_뒤_시작한_공구는_새_값() {
        String code = "P" + Long.toString(kit.ids.nextId(), 36).toUpperCase();
        kit.policies.schedule(new ShippingPolicyService.PolicyInput(code, "기본", 3_000, 50_000L, 3_000, "ONE_WAY", null), kit.operator, "ip");
        long running = kit.spec(code).period(kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofDays(20))).open();
        Instant raise = kit.now().plus(Duration.ofDays(10));
        kit.policies.schedule(new ShippingPolicyService.PolicyInput(code, "기본", 3_500, 50_000L, 3_000, "ONE_WAY", raise), kit.operator, "ip");

        kit.advance(Duration.ofDays(11));
        long later = kit.spec(code).open();
        assertThat(kit.group(running).shippingFee()).isEqualTo(3_000);
        assertThat(kit.group(later).shippingFee()).isEqualTo(3_500);
        assertThat(kit.order(kit.newMember(), running, 1).amount()).as("진행 중 공구는 시작 때 배송비").isEqualTo(28_000);
        assertThat(kit.policies.history(code)).hasSize(2);
        assertThat(kit.policies.history(code).get(0).effectiveTo()).isEqualTo(raise);
        assertThat(kit.audits).filteredOn(a -> a.action().equals(AuditActions.COMMERCE_SHIPPING_POLICY_CREATE)).hasSize(2);
    }

    @Test
    void 지난_시각이나_이미_있는_예약보다_앞선_시각은_넣지_못한다() {
        String code = "Q" + Long.toString(kit.ids.nextId(), 36).toUpperCase();
        kit.policies.schedule(new ShippingPolicyService.PolicyInput(code, "기본", 3_000, null, 3_000, null, null), kit.operator, "ip");
        kit.policies.schedule(new ShippingPolicyService.PolicyInput(code, "기본", 3_500, null, 3_000, null, kit.now().plus(Duration.ofDays(5))), kit.operator, "ip");
        assertThatThrownBy(() -> kit.policies.schedule(new ShippingPolicyService.PolicyInput(code, "기본", 4_000, null, 3_000, null, kit.now().plus(Duration.ofDays(3))),
                kit.operator, "ip")).isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.SHIPPING_POLICY_CONFLICT));
        assertThatThrownBy(() -> kit.policies.schedule(new ShippingPolicyService.PolicyInput(code, "기본", 4_000, null, 3_000, null, kit.now().minus(Duration.ofDays(1))),
                kit.operator, "ip")).isInstanceOf(GroupBuyException.class);
        assertThatThrownBy(() -> kit.policies.schedule(new ShippingPolicyService.PolicyInput("bad code", "", -1, 0L, 3_000, "SIDEWAYS", null), kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKeys("code", "name", "baseFee", "returnFeeMode"));
    }

    @Test
    void 적용이_시작된_정책_줄은_고치거나_지울_수_없고_기간은_겹칠_수_없다_DB() {
        String code = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long id = kit.jdbc.queryForObject("SELECT id FROM shipping_policies WHERE code = ?", Long.class, code);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE shipping_policies SET base_fee = 4000 WHERE id = ?", id)).hasMessageContaining("고칠 수 없다");
        assertThatThrownBy(() -> kit.jdbc.update("DELETE FROM shipping_policies WHERE id = ?", id)).hasMessageContaining("지울 수 없다");
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE shipping_policies SET effective_to = now() - interval '1 day' WHERE id = ?", id))
                .hasMessageContaining("지금 이후");
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO shipping_policies (id, code, name, base_fee, return_fee, effective_from, created_at) VALUES (?, ?, 'x', 1, 1, ?, now())",
                kit.ids.nextId(), code, Timestamp.from(Instant.now().plus(Duration.ofDays(3))))).hasMessageContaining("ex_shipping_policies_period");
        assertThat(kit.jdbc.update("UPDATE shipping_policies SET effective_to = now() + interval '30 days' WHERE id = ?", id)).as("끝만 미래로").isEqualTo(1);
    }
}
