package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.dto.ShippingAddress;
import com.community.groupbuy.entity.CollectionMethod;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.RequestReason;
import com.community.groupbuy.entity.RequestType;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.FulfillmentStore;
import com.community.groupbuy.service.AddressRevealService;
import com.community.groupbuy.service.FulfillmentService;
import com.community.groupbuy.service.FulfillmentService.ShipLine;
import com.community.groupbuy.service.GroupBuyRequestService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 발주·준비중·발송·배송완료·송장 정정·에스크로·택배사·배송지 보호·명단 내보내기 — commerce.md 10-3·10-9, 계약 결정 7·9·11·14·20·30·43·47·64, G10·G21(수식 주입).
 */
class GroupBuyFulfillmentTest {

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
        return kit.policy(3_000, 50_000L, 3_000, "ONE_WAY");
    }

    @Test
    void 택배사는_시드_6개이고_발송_이력이_있으면_지우지_못하고_끄기만() {
        assertThat(kit.fulfillment.carriers()).extracting(FulfillmentStore.CarrierRow::code).contains("CJ", "HANJIN", "LOTTE", "EPOST", "LOGEN", "KDEXP");
        long gb = kit.spec(policy()).open();
        JoinResult paid = kit.paidOrder(kit.newMember(), gb, 1);
        kit.ship(paid.participationId());
        List<FulfillmentStore.CarrierRow> withoutCj = kit.fulfillment.carriers().stream().filter(c -> !c.code().equals("CJ")).toList();
        assertThatThrownBy(() -> kit.fulfillment.saveCarriers(withoutCj, kit.operator, "127.0.0.1"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.CARRIER_IN_USE));
        assertThatThrownBy(() -> kit.fulfillment.saveCarriers(List.of(new FulfillmentStore.CarrierRow("XX", "x", "http://a/{no}", true, 1)), kit.operator, "127.0.0.1"))
                .as("https 와 {no} 한 번").isInstanceOf(com.community.zconfiguration.exception.ValidationException.class);
    }

    @Test
    void 송장은_숫자_영문만_남겨_6에서_30자() {
        assertThat(FulfillmentService.normalizeTracking(" 1234-5678 90 ")).isEqualTo("1234567890");
        assertThatThrownBy(() -> FulfillmentService.normalizeTracking("12-34")).isInstanceOfSatisfying(GroupBuyException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.TRACKING_INVALID));
        assertThatThrownBy(() -> FulfillmentService.normalizeTracking("1".repeat(31))).isInstanceOf(GroupBuyException.class);
    }

    @Test
    void 발송_일괄은_행마다_결과_열린_요청이_있거나_송장이_틀린_줄만_실패하고_처음_배송비를_고정한다() {
        long gb = kit.spec(policy()).open();
        long a = kit.newMember();
        long b = kit.newMember();
        long c = kit.newMember();
        JoinResult pa = kit.paidOrder(a, gb, 1);
        JoinResult pb = kit.paidOrder(b, gb, 1);
        JoinResult pc = kit.paidOrder(c, gb, 3); // 무료배송
        kit.requests.create(b, pb.participationId(), new GroupBuyRequestService.RequestInput(RequestType.CANCEL, null, null,
                List.of(new GroupBuyRequestService.LineInput(kit.mainSku(gb), 1, null)), null, null, null));
        kit.cancels.cancelByMember(c, pc.participationId(), lines(kit.mainSku(gb), 2)); // 재부과 3,000

        var result = kit.fulfillment.ship(List.of(new ShipLine(pa.participationId(), "CJ", "1111-2222-3333"), new ShipLine(pb.participationId(), "CJ", "222233334444"),
                new ShipLine(pc.participationId(), "NOPE", "333344445555"), new ShipLine(pc.participationId(), "HANJIN", "x")), kit.operator, "127.0.0.1");

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.results()).extracting(FulfillmentService.RowResult::code).containsExactly(null, GroupBuyException.ORDER_NOT_SHIPPABLE,
                GroupBuyException.TRACKING_INVALID, GroupBuyException.TRACKING_INVALID);
        var shipped = kit.participation(pa.participationId());
        assertThat(shipped.status()).isEqualTo(ParticipationStatus.SHIPPED);
        assertThat(shipped.shippedShippingFee()).isEqualTo(3_000);
        assertThat(kit.jdbc.queryForObject("SELECT tracking_no FROM group_buy_shipments WHERE participation_id = ?", String.class, pa.participationId()))
                .isEqualTo("111122223333");
        kit.ship(pc.participationId());
        assertThat(kit.participation(pc.participationId()).shippedShippingFee()).as("발송 전 재부과는 처음 배송비 — 결정 43").isEqualTo(3_000);
        assertThat(kit.audits).anySatisfy(e -> assertThat(e.action()).isEqualTo(AuditActions.GROUPBUY_FULFILLMENT));
        kit.assertInvariants(gb);
    }

    @Test
    void 마감_후_발주_공구는_발주_뒤에만_준비중_발송_첫_발송이_SHIPPING_배송완료는_운영자가() {
        long gb = kit.spec(policy()).qty(1, 10).period(kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofMinutes(10))).open();
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, 2);
        kit.advance(Duration.ofMinutes(20));
        kit.runStateWorker();
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.CLOSED_SUCCESS);
        assertThat(kit.fulfillment.prepare(List.of(paid.participationId()), kit.operator, "ip").results().getFirst().code()).isEqualTo(GroupBuyException.ORDER_NOT_PREPARABLE);
        assertThat(kit.fulfillment.ship(List.of(new ShipLine(paid.participationId(), "CJ", "123456789")), kit.operator, "ip").failed()).isEqualTo(1);

        kit.fulfillment.markOrdered(gb, kit.operator, "ip");
        assertThatThrownBy(() -> kit.fulfillment.markOrdered(gb, kit.operator, "ip")).isInstanceOfSatisfying(GroupBuyException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.NOT_ORDERABLE));
        assertThat(kit.fulfillment.prepare(List.of(paid.participationId()), kit.operator, "ip").succeeded()).isEqualTo(1);
        kit.ship(paid.participationId());
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.SHIPPING);
        kit.runStateWorker();
        assertThat(kit.group(gb).status()).as("발송 중 주문이 있으면 완료가 아니다").isEqualTo(GroupBuyStatus.SHIPPING);
        kit.deliver(paid.participationId());
        assertThat(kit.participation(paid.participationId()).deliveredAt()).isNotNull();
        kit.runStateWorker();
        assertThat(kit.group(gb).status()).as("결정 9 — 1분 워커 sweep").isEqualTo(GroupBuyStatus.COMPLETED);
        kit.assertInvariants(gb);
    }

    @Test
    void 에스크로_배송_정보는_발송_뒤_커밋_밖에서_실패하면_재시도_표_송장_정정은_수정으로_다시() {
        long gb = kit.spec(policy()).open();
        JoinResult paid = kit.paidOrder(kit.newMember(), gb, 1);
        String paymentId = paid.checkout().paymentId();
        kit.escrowFailures.set(1);
        kit.ship(paid.participationId());
        var shipment = kit.spring.getBean(FulfillmentStore.class).original(paid.participationId()).orElseThrow();
        assertThat(shipment.escrowLogisticsAt()).as("첫 시도 실패").isNull();
        var job = kit.spring.getBean(FulfillmentStore.class).escrowJobs(shipment.id()).getFirst();
        assertThat(job.status()).isEqualTo("PENDING");
        assertThat(job.attempts()).isEqualTo(1);
        assertThat(job.nextAttemptAt()).as("재시도 표 첫 칸 1분").isAfter(kit.now().plus(Duration.ofSeconds(50)));

        kit.advance(Duration.ofMinutes(2));
        kit.escrowWorker.runOnce();
        shipment = kit.spring.getBean(FulfillmentStore.class).original(paid.participationId()).orElseThrow();
        assertThat(shipment.escrowLogisticsAt()).isNotNull();
        assertThat(kit.fakeGateway.escrowCalls()).anySatisfy(c -> {
            assertThat(c.paymentId()).isEqualTo(paymentId);
            assertThat(c.kind()).isEqualTo("LOGISTICS");
        });

        kit.fulfillment.correctTracking(paid.participationId(), "LOTTE", "999988887777", kit.operator, "ip");
        assertThat(kit.fakeGateway.escrowCalls()).anySatisfy(c -> {
            assertThat(c.paymentId()).isEqualTo(paymentId);
            assertThat(c.kind()).isEqualTo("LOGISTICS_UPDATE");
            assertThat(c.logistics().trackingNo()).isEqualTo("999988887777");
        });
    }

    @Test
    void 에스크로_호출이_여섯_번_실패하면_그만두고_이벤트() {
        long gb = kit.spec(policy()).open();
        JoinResult paid = kit.paidOrder(kit.newMember(), gb, 1);
        kit.escrowFailures.set(100);
        kit.ship(paid.participationId());
        for (int i = 0; i < 6; i++) {
            kit.advance(Duration.ofHours(7));
            kit.escrowWorker.runOnce();
        }
        var shipment = kit.spring.getBean(FulfillmentStore.class).original(paid.participationId()).orElseThrow();
        assertThat(kit.spring.getBean(FulfillmentStore.class).escrowJobs(shipment.id()).getFirst().status()).isEqualTo("GAVE_UP");
        assertThat(kit.eventsOf(com.community.groupbuy.dto.GroupBuyEvents.EscrowGaveUp.class)).anySatisfy(e -> assertThat(e.shipmentId()).isEqualTo(shipment.id()));
    }

    @Test
    void 결정14_구매_확정은_배송완료_7일이_열린_요청_없이_지난_뒤() {
        long gb = kit.spec(policy()).open();
        long a = kit.newMember();
        long b = kit.newMember();
        JoinResult pa = kit.delivered(a, gb, lines(kit.mainSku(gb), 1));
        JoinResult pb = kit.delivered(b, gb, lines(kit.mainSku(gb), 1));
        kit.requests.create(b, pb.participationId(), new GroupBuyRequestService.RequestInput(RequestType.RETURN, CollectionMethod.CUSTOMER_SEND, null,
                List.of(new GroupBuyRequestService.LineInput(kit.mainSku(gb), 1, RequestReason.DEFECT)), null, null, null));
        kit.advance(Duration.ofDays(6));
        kit.escrowWorker.runOnce();
        assertThat(completed(pa)).isFalse();
        kit.advance(Duration.ofDays(2));
        kit.escrowWorker.runOnce();
        assertThat(completed(pa)).isTrue();
        assertThat(completed(pb)).as("열린 반품 요청").isFalse();
    }

    private boolean completed(JoinResult joined) {
        return kit.spring.getBean(FulfillmentStore.class).original(joined.participationId()).orElseThrow().escrowCompletedAt() != null;
    }

    // ── 배송지(10-9 · 결정 30) ─────────────────────────────────────────────

    @Test
    void 가린_값과_운영자_열람은_사유와_기록_뒤에만() {
        long gb = kit.spec(policy()).open();
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, 1);
        ShippingAddress masked = kit.addresses.masked(paid.participationId());
        assertThat(masked.recipient()).isEqualTo("홍*동");
        assertThat(masked.phone()).isEqualTo("010-****-5678");
        assertThat(masked.address1()).isEqualTo("서울시 강남구 …");
        assertThat(masked.address2()).isNull();
        assertThat(AddressRevealService.maskName("김수")).isEqualTo("김*");

        assertThatThrownBy(() -> kit.addresses.reveal(paid.participationId(), "짧음", kit.operator, "ip")).isInstanceOf(com.community.zconfiguration.exception.ValidationException.class);
        var revealed = kit.addresses.reveal(paid.participationId(), "택배 접수 확인", kit.operator, "ip");
        assertThat(revealed.address().address2()).isEqualTo("101호");
        assertThat(revealed.hideAt()).isAfter(kit.now().plus(Duration.ofMinutes(9)));
        assertThat(kit.count("SELECT count(*) FROM commerce_address_views WHERE participation_id = ? AND kind = 'REVEAL' AND reason = '택배 접수 확인'", paid.participationId()))
                .isEqualTo(1);
        assertThat(kit.audits).anySatisfy(e -> {
            assertThat(e.action()).isEqualTo(AuditActions.GROUPBUY_ADDRESS_REVEAL);
            assertThat(e.toString()).doesNotContain("테헤란").doesNotContain("1234-5678");
        });
        assertThat(kit.addresses.memberView(member, paid.participationId()).recipient()).isEqualTo("홍길동");
        assertThatThrownBy(() -> kit.addresses.memberView(kit.newMember(), paid.participationId())).isInstanceOf(GroupBuyException.class);
    }

    @Test
    void G10_즉시_발송_공구는_준비중_전까지_배송지_수정_명단은_언제든() {
        long gb = kit.spec(policy()).open();
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, 1);
        ShippingAddress next = new ShippingAddress("임꺽정", "010-9999-0000", "12345", "부산시 해운대구 1", null, null);
        kit.addresses.memberUpdate(member, paid.participationId(), next);
        assertThat(kit.addresses.memberView(member, paid.participationId()).recipient()).isEqualTo("임꺽정");
        var export = kit.addresses.export(gb, "택배 접수용 명단", kit.operator, "ip");
        assertThat(export.rowCount()).isEqualTo(1);
        assertThat(export.csv()).contains("임꺽정");
        kit.fulfillment.prepare(List.of(paid.participationId()), kit.operator, "ip");
        assertThatThrownBy(() -> kit.addresses.memberUpdate(member, paid.participationId(), next)).isInstanceOfSatisfying(GroupBuyException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.ADDRESS_LOCKED));
        assertThat(kit.addresses.export(gb, "택배 접수용 명단", kit.operator, "ip").rowCount()).as("준비중도 발송 전").isEqualTo(1);
        kit.ship(paid.participationId());
        assertThat(kit.addresses.export(gb, "택배 접수용 명단", kit.operator, "ip").rowCount()).as("발송된 주문은 빠진다").isZero();
        assertThat(kit.audits).anySatisfy(e -> assertThat(e.action()).isEqualTo(AuditActions.GROUPBUY_SHIPPING_EXPORT));
    }

    @Test
    void G10_마감_후_발주_공구는_발주_전에만_수정_명단은_발주_뒤에만() {
        long gb = kit.spec(policy()).qty(1, 10).period(kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofMinutes(10))).open();
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, 1);
        kit.advance(Duration.ofMinutes(20));
        kit.runStateWorker();
        ShippingAddress next = new ShippingAddress("임꺽정", "010-9999-0000", "12345", "부산시 해운대구 1", null, null);
        kit.addresses.memberUpdate(member, paid.participationId(), next);
        assertThatThrownBy(() -> kit.addresses.export(gb, "택배 접수용 명단", kit.operator, "ip")).isInstanceOfSatisfying(GroupBuyException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.EXPORT_NOT_READY));
        kit.fulfillment.markOrdered(gb, kit.operator, "ip");
        assertThatThrownBy(() -> kit.addresses.memberUpdate(member, paid.participationId(), next)).isInstanceOfSatisfying(GroupBuyException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.ADDRESS_LOCKED));
        assertThat(kit.addresses.export(gb, "택배 접수용 명단", kit.operator, "ip").rowCount()).isEqualTo(1);
    }

    @Test
    void G21_CSV_수식_주입_방어() {
        assertThat(AddressRevealService.csvCell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(AddressRevealService.csvCell("+82")).isEqualTo("\"'+82\"");
        assertThat(AddressRevealService.csvCell("-1")).isEqualTo("\"'-1\"");
        assertThat(AddressRevealService.csvCell("@SUM")).isEqualTo("\"'@SUM\"");
        assertThat(AddressRevealService.csvCell("홍길동")).isEqualTo("\"홍길동\"");
        long gb = kit.spec(policy()).open();
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, 1);
        kit.addresses.memberUpdate(member, paid.participationId(), new ShippingAddress("=cmd|' /C calc'!A0", "010-1111-2222", "12345", "@주소 1", null, "+메모"));
        String csv = kit.addresses.export(gb, "택배 접수용 명단", kit.operator, "ip").csv();
        assertThat(csv).contains("\"'=cmd|' /C calc'!A0\"").contains("\"'@주소 1\"").contains("\"'+메모\"");
    }

    @Test
    void G10_발송된_주문이_있는_즉시_발송_공구를_중단하면_발송_전_주문만_환불() {
        long gb = kit.spec(policy()).open();
        JoinResult shipped = kit.paidOrder(kit.newMember(), gb, 1);
        JoinResult waiting = kit.paidOrder(kit.newMember(), gb, 1);
        kit.ship(shipped.participationId());
        kit.admin.cancel(gb, "공급처 사정으로 중단", kit.operator, "ip");
        kit.runRefundWorker();
        assertThat(kit.participation(shipped.participationId()).status()).isEqualTo(ParticipationStatus.SHIPPED);
        assertThat(kit.participation(waiting.participationId()).status()).isEqualTo(ParticipationStatus.REFUNDED);
        kit.deliver(shipped.participationId());
        assertThat(kit.participation(shipped.participationId()).status()).as("결정 49 — 중단 뒤에도 배송완료는 계속").isEqualTo(ParticipationStatus.DELIVERED);
        kit.assertInvariants(gb);
    }
}
