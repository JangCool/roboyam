package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.address;
import static com.community.groupbuy.GroupBuyTestKit.buyer;
import static com.community.groupbuy.GroupBuyTestKit.lines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.groupbuy.dto.GroupBuyBuyer;
import com.community.groupbuy.dto.JoinRequest;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.exception.GroupBuyBlockedException;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.identity.exception.IdentityRequiredException;
import com.community.identity.exception.IdentityUnderageException;
import com.community.payment.dto.PaymentStatus;
import com.community.restriction.exception.MemberRestrictedException;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 공구 주문(참여) — commerce.md 10-4·10-5·10-6, 계약 결정 23·24·28·41·59·66·72·77·85·86, G5·G19·G20·G27·G42. 실제 PostgreSQL.
 */
class GroupBuyJoinTest {

    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    @Test
    void 주문하면_결제_중_주문과_선점_줄과_결제가_생기고_선점_끝은_결제_유효_시각과_같다() {
        String policy = kit.policy(3_000, 50_000L, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).open();
        long member = kit.newMember();

        JoinResult joined = kit.order(member, gb, 2);

        var p = kit.participation(joined.participationId());
        assertThat(p.status()).isEqualTo(ParticipationStatus.PENDING_PAYMENT);
        assertThat(p.orderNo()).matches("G\\d{8}\\d{5}");
        assertThat(p.itemsAmount()).isEqualTo(50_000);
        assertThat(p.shippingFee()).as("무료배송 기준 이상").isZero();
        assertThat(p.cashAmount()).isEqualTo(50_000);
        assertThat(p.reservedUntil()).as("선점 5분").isEqualTo(kit.now().plus(Duration.ofMinutes(5)));
        assertThat(kit.payment(joined.checkout().paymentId()).expiresAt()).as("결정 41").isEqualTo(p.reservedUntil());
        assertThat(kit.payment(joined.checkout().paymentId()).amount()).isEqualTo(50_000);
        assertThat(kit.group(gb).reservedQty()).isEqualTo(2);
        assertThat(kit.count("SELECT count(*) FROM group_buy_qty_movements WHERE participation_id = ? AND kind = 'RESERVE'", p.id())).isEqualTo(1);
        // 배송지는 암호문으로만 — 평문이 표에 없다
        assertThat(kit.count("SELECT count(*) FROM group_buy_participations WHERE id = ? AND (recipient_enc LIKE '%홍길동%' OR address1_enc LIKE '%테헤란%' "
                + "OR recipient_enc NOT LIKE 'd1:%' OR phone_enc NOT LIKE 'd1:%')", p.id())).isZero();
        kit.assertInvariants(gb);
    }

    @Test
    void 배송비는_주문_전체_상품_금액으로_무료배송_기준_아래면_붙는다() {
        String policy = kit.policy(3_000, 50_000L, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).open();
        JoinResult joined = kit.order(kit.newMember(), gb, 1);
        assertThat(kit.participation(joined.participationId()).shippingFee()).isEqualTo(3_000);
        assertThat(joined.amount()).isEqualTo(28_000);
    }

    @Test
    void G42_동의를_하나라도_안_하거나_줄이_없으면_400_이고_아무것도_만들지_않는다() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).open();
        long member = kit.newMember();
        for (JoinRequest bad : List.of(
                new JoinRequest(gb, lines(null, 1), address(), false, true, true, 28_000L),
                new JoinRequest(gb, lines(null, 1), address(), true, false, true, 28_000L),
                new JoinRequest(gb, lines(null, 1), address(), true, true, false, 28_000L),
                new JoinRequest(gb, List.of(), address(), true, true, true, 28_000L))) {
            assertThatThrownBy(() -> kit.join.join(buyer(member), bad)).isInstanceOf(ValidationException.class);
        }
        assertThat(kit.count("SELECT count(*) FROM group_buy_participations WHERE user_id = ?", member)).isZero();
        assertThat(kit.group(gb).reservedQty()).isZero();
    }

    @Test
    void G42_화면이_본_금액과_다르면_409_AMOUNT_CHANGED_이고_선점하지_않는다() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).open();
        long member = kit.newMember();
        assertThatThrownBy(() -> kit.join.join(buyer(member), new JoinRequest(gb, lines(null, 1), address(), true, true, true, 25_000L)))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.AMOUNT_CHANGED);
                    assertThat(e.getExtras()).containsEntry("amount", 28_000L);
                });
        assertThatThrownBy(() -> kit.join.join(buyer(member), new JoinRequest(gb, lines(null, 1), address(), true, true, true, null)))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.AMOUNT_CHANGED));
        assertThat(kit.group(gb).reservedQty()).isZero();
        assertThat(kit.count("SELECT count(*) FROM group_buy_participations WHERE user_id = ?", member)).isZero();
    }

    @Test
    void 자격_권한_본인인증_19세_참여_제한_판매_스위치() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).open();

        long noPermission = kit.newMember();
        assertThatThrownBy(() -> kit.join.join(new GroupBuyBuyer(noPermission, false), request(gb)))
                .isInstanceOfSatisfying(GroupBuyBlockedException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyBlockedException.PERMISSION_DENIED));

        long unverified = kit.newMemberWithoutIdentity();
        assertThatThrownBy(() -> kit.join.join(buyer(unverified), request(gb))).isInstanceOf(IdentityRequiredException.class);

        long minor = kit.newMemberWithoutIdentity();
        kit.verify(minor, LocalDate.now().minusYears(18));
        assertThatThrownBy(() -> kit.join.join(buyer(minor), request(gb))).isInstanceOf(IdentityUnderageException.class);

        long suspended = kit.newMember();
        kit.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended);
        assertThatThrownBy(() -> kit.join.join(buyer(suspended), request(gb)))
                .isInstanceOfSatisfying(GroupBuyBlockedException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyBlockedException.ACCOUNT_RESTRICTED));

        long banned = kit.newMember();
        kit.jdbc.update("INSERT INTO member_restrictions (id, user_id, type, reason, starts_at, source, auto_rule, created_at) VALUES (?, ?, 'GROUP_BUY_BAN', '시험', ?, 'AUTO', "
                + "'GROUP_BUY_HOLD_EXPIRED', ?)", kit.ids.nextId(), banned, Timestamp.from(kit.now().minusSeconds(60)), Timestamp.from(kit.now()));
        assertThatThrownBy(() -> kit.join.join(buyer(banned), request(gb))).isInstanceOf(MemberRestrictedException.class);

        long ok = kit.newMember();
        kit.salesOpen = false;
        assertThatThrownBy(() -> kit.join.join(buyer(ok), request(gb)))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.SALES_CLOSED));
        kit.salesOpen = true;
        assertThat(kit.join.join(buyer(ok), request(gb)).amount()).isEqualTo(28_000);
        assertThat(kit.group(gb).reservedQty()).isEqualTo(1);
    }

    @Test
    void 모집_전_마감_뒤_DRAFT_는_주문을_받지_않는다() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        var spec = kit.spec(policy);
        spec.period(kit.now().plus(Duration.ofHours(1)), kit.now().plus(Duration.ofDays(1)));
        long upcoming = spec.open();
        assertThat(kit.group(upcoming).status().name()).isEqualTo("DRAFT");
        assertThatThrownBy(() -> kit.join.join(buyer(kit.newMember()), new JoinRequest(upcoming, lines(null, 1), address(), true, true, true, 28_000L)))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.NOT_RECRUITING));

        long ending = kit.spec(policy).period(kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofMinutes(10))).open();
        kit.advance(Duration.ofMinutes(11));
        assertThatThrownBy(() -> kit.join.join(buyer(kit.newMember()), new JoinRequest(ending, lines(null, 1), address(), true, true, true, 28_000L)))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.NOT_RECRUITING));
    }

    @Test
    void 진행_중_주문은_공구당_한_건이고_전부_취소하면_다시_주문할_수_있다_G19() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).open();
        long member = kit.newMember();
        JoinResult first = kit.paidOrder(member, gb, 1);
        assertThatThrownBy(() -> kit.order(member, gb, 1))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.ALREADY_JOINED));

        var canceled = kit.cancels.cancelByMember(member, first.participationId(), null);
        assertThat(canceled.status()).isEqualTo(ParticipationStatus.CANCELED);
        JoinResult again = kit.order(member, gb, 1);
        assertThat(again.participationId()).isNotEqualTo(first.participationId());
        kit.assertInvariants(gb);
    }

    @Test
    void 같은_회원이_동시에_두_번_눌러도_진행_중_주문은_하나다() throws Exception {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).open();
        long member = kit.newMember();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> kit.order(member, gb, 1));
        }
        List<Object> results = GroupBuyTestKit.runTogether(tasks);
        assertThat(results.stream().filter(JoinResult.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(r -> r instanceof GroupBuyException e && e.getErrorCode().equals(GroupBuyException.ALREADY_JOINED))).hasSize(3);
        assertThat(kit.group(gb).reservedQty()).isEqualTo(1);
        kit.assertInvariants(gb);
    }

    @Test
    void 마지막_하나를_여럿이_동시에_사면_하나만_선점하고_나머지는_잠시_품절() throws Exception {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).qty(0, 1).open();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            long member = kit.newMember();
            tasks.add(() -> kit.order(member, gb, 1));
        }
        List<Object> results = GroupBuyTestKit.runTogether(tasks);
        assertThat(results.stream().filter(JoinResult.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(r -> r instanceof GroupBuyException e && e.getErrorCode().equals(GroupBuyException.SOLD_OUT)
                && Boolean.TRUE.equals(e.getExtras().get("temporary")))).as("결제 중 선점 때문 — 잠시 품절").hasSize(4);
        kit.assertInvariants(gb);
    }

    @Test
    void 다_팔리면_temporary_false_로_구분한다() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).qty(0, 2).open();
        kit.paidOrder(kit.newMember(), gb, 1);
        kit.paidOrder(kit.newMember(), gb, 1);
        // 다 팔려 CLOSING 이 됐다 — 주문은 모집 중 아님으로
        assertThatThrownBy(() -> kit.order(kit.newMember(), gb, 1))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.NOT_RECRUITING));
        var availability = kit.quotes.availability(gb);
        assertThat(availability.soldOut()).isTrue();
        assertThat(availability.temporarilyUnavailable()).isFalse();
    }

    @Test
    void G5_여러_줄_중_한_줄이라도_모자라면_주문_전체가_실패하고_아무것도_선점하지_않는다() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).option("빨강", 0, 5).option("파랑", 1_000, 1).option("검정", 0, null).open();
        long red = kit.optionId(gb, "빨강");
        long blue = kit.optionId(gb, "파랑");
        long black = kit.optionId(gb, "검정");

        assertThatThrownBy(() -> kit.order(kit.newMember(), gb, lines(red, 2, blue, 2, black, 1)))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.OPTION_SOLD_OUT);
                    assertThat(e.getExtras()).containsEntry("optionId", Long.toString(blue));
                });
        assertThat(kit.group(gb).reservedQty()).isZero();
        assertThat(kit.options(gb)).allSatisfy(o -> assertThat(o.reservedQty()).isZero());

        JoinResult ok = kit.order(kit.newMember(), gb, lines(red, 2, blue, 1, black, 3));
        assertThat(ok.amount()).isEqualTo(25_000 * 2 + 26_000 + 25_000 * 3 + 3_000);
        assertThat(kit.group(gb).reservedQty()).isEqualTo(6);
        assertThat(kit.option(gb, black).reservedQty()).as("한도 없는 옵션도 수량 열을 갱신(결정 77)").isEqualTo(3);
        assertThat(kit.items(ok.participationId())).hasSize(3);
        kit.assertInvariants(gb);
    }

    @Test
    void 옵션이_있는_공구는_옵션을_골라야_하고_같은_옵션_두_줄은_400() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).option("빨강", 0, 5).open();
        long red = kit.optionId(gb, "빨강");
        assertThatThrownBy(() -> kit.join.join(buyer(kit.newMember()), new JoinRequest(gb, lines(null, 1), address(), true, true, true, 28_000L)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> kit.join.join(buyer(kit.newMember()), new JoinRequest(gb, lines(red, 1, red, 1), address(), true, true, true, 53_000L)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void 한_사람_한도는_결제_중_선점과_산_수량_빼기_취소를_센다() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).perUser(3).open();
        long member = kit.newMember();
        JoinResult first = kit.paidOrder(member, gb, 2);
        kit.cancels.cancelByMember(member, first.participationId(), lines(null, 1)); // 1 취소 — 이제 1개 가짐
        // 진행 중 주문이 있어 새 주문은 막히지만 한도 계산 자체를 본다: 배송완료로 끝낸 뒤 새 주문
        kit.jdbc.update("UPDATE group_buy_participations SET status = 'DELIVERED' WHERE id = ?", first.participationId());
        assertThatThrownBy(() -> kit.order(member, gb, 3))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.LIMIT_EXCEEDED);
                    assertThat(e.getExtras()).containsEntry("remaining", 2);
                });
        assertThat(kit.order(member, gb, 2).amount()).isEqualTo(53_000);
    }

    @Test
    void 반품은_처리_방법과_관계없이_1인_한도에서_빼고_금액만_환불은_그대로_센다_G20() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).perUser(2).open();
        long member = kit.newMember();
        JoinResult first = kit.paidOrder(member, gb, 2);
        // 2b-3 의 반품 완료(폐기)를 흉내: 1개 반품(returned_qty), 판매 수량은 그대로
        kit.jdbc.update("UPDATE group_buy_participation_items SET returned_qty = 1 WHERE participation_id = ?", first.participationId());
        kit.jdbc.update("UPDATE group_buy_participations SET status = 'DELIVERED' WHERE id = ?", first.participationId());
        assertThat(kit.order(member, gb, 1).amount()).as("반품 1개만큼 한도가 풀린다").isEqualTo(28_000);

        long other = kit.newMember();
        JoinResult paid = kit.paidOrder(other, gb, 2);
        // 금액만 돌려준 운영 환불(수량 이동 없음)은 산 수량 그대로
        kit.jdbc.update("UPDATE group_buy_participations SET status = 'DELIVERED', refunded_amount = 5000 WHERE id = ?", paid.participationId());
        assertThatThrownBy(() -> kit.order(other, gb, 1))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.LIMIT_EXCEEDED));
    }

    @Test
    void 차수_사이_한도는_기본_독립이고_합산을_켜면_루트_전체를_센다_G8_G20() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long independentRoot = kit.spec(policy).qty(0, 2).perUser(2).open();
        long sharedRoot = kit.spec(policy).qty(0, 2).perUser(2).shared().open();
        long member = kit.newMember();
        kit.paidOrder(member, independentRoot, 2);
        kit.paidOrder(member, sharedRoot, 2);
        assertThat(kit.group(independentRoot).status().name()).isEqualTo("CLOSING");
        var independent2 = kit.admin.createRound(independentRoot, kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofDays(1)), null, kit.operator, "ip");
        var shared2 = kit.admin.createRound(sharedRoot, kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofDays(1)), null, kit.operator, "ip");
        kit.admin.publish(independent2.groupBuyId(), kit.group(independent2.groupBuyId()).version(), kit.operator, "ip");
        kit.admin.publish(shared2.groupBuyId(), kit.group(shared2.groupBuyId()).version(), kit.operator, "ip");

        assertThat(kit.order(member, independent2.groupBuyId(), 2).amount()).as("독립 — 2차에서 다시 2개").isEqualTo(53_000);
        assertThatThrownBy(() -> kit.order(member, shared2.groupBuyId(), 1))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.LIMIT_EXCEEDED));
    }

    @Test
    void 본인의_지난_선점은_주문할_때_그_자리에서_정리되고_남의_것은_건드리지_않는다_결정85() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).open();
        long me = kit.newMember();
        long other = kit.newMember();
        JoinResult mine = kit.order(me, gb, 1);
        JoinResult theirs = kit.order(other, gb, 1);
        kit.advance(Duration.ofMinutes(6));

        JoinResult again = kit.order(me, gb, 1);

        assertThat(kit.participation(mine.participationId()).status()).isEqualTo(ParticipationStatus.EXPIRED);
        assertThat(kit.payment(mine.checkout().paymentId()).status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(kit.participation(theirs.participationId()).status()).as("남의 주문은 워커가").isEqualTo(ParticipationStatus.PENDING_PAYMENT);
        assertThat(kit.participation(again.participationId()).status()).isEqualTo(ParticipationStatus.PENDING_PAYMENT);
        kit.assertInvariants(gb);
    }

    @Test
    void 없는_공구_비공개_DRAFT_는_404() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long draft = kit.spec(policy).create();
        assertThatThrownBy(() -> kit.join.join(buyer(kit.newMember()), new JoinRequest(draft, lines(null, 1), address(), true, true, true, 28_000L)))
                .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.NOT_FOUND));
    }

    private static JoinRequest request(long gb) {
        return new JoinRequest(gb, lines(null, 1), address(), true, true, true, 28_000L);
    }
}
