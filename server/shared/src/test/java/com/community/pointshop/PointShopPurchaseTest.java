package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.point.entity.PointReason;
import com.community.point.exception.PointInsufficientException;
import com.community.point.service.PointService;
import com.community.pointshop.dto.PointShopBuyer;
import com.community.pointshop.dto.response.PointShopPurchaseResultView;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopSaleState;
import com.community.pointshop.entity.PointShopUsageType;
import com.community.pointshop.exception.PointShopAlreadyOwnedException;
import com.community.pointshop.exception.PointShopBlockedException;
import com.community.pointshop.exception.PointShopItemNotFoundException;
import com.community.pointshop.exception.PointShopNotOnSaleException;
import com.community.pointshop.exception.PointShopSoldOutException;
import com.community.restriction.exception.MemberRestrictedException;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 포인트 상점 구매(commerce.md 7-2, 계약 「B」 구매·결정 9) — 실제 PostgreSQL. 재고 조건부 차감·영구 중복·판매 기간·동결·잔액 부족·적립부터 차감·가격 사본·기간제 이어 붙이기.
 * 같은 멱등 키로 다시 사기는 멱등 장치({@code MoneyRequestGuard})가 api 에 있어 api 단계 시험이 막는다 — 여기서는 같은 구매 대상에 포인트를 두 번 쓰지 못하는 것까지.
 */
class PointShopPurchaseTest extends PointShopTestBase {

    @Test
    void 마지막_재고_하나를_둘이_동시에_사면_하나만_성공하고_재고는_0_불변식이_유지된다() throws Exception {
        long item = kit.border(500, "RAINBOW", 1);
        long a = kit.newMember();
        long b = kit.newMember();
        kit.grant(a, 1000);
        kit.grant(b, 1000);

        List<Callable<String>> jobs = new ArrayList<>();
        for (long member : new long[] {a, b}) {
            jobs.add(() -> {
                try {
                    kit.buy(member, item);
                    return "OK";
                } catch (PointShopSoldOutException e) {
                    return e.getErrorCode();
                }
            });
        }
        List<String> results = PointShopTestKit.runTogether(jobs);

        assertThat(results).containsExactlyInAnyOrder("OK", "POINT_SHOP_SOLD_OUT");
        assertThat(kit.stock(item)).isZero();
        assertThat(kit.count("SELECT count(*) FROM point_shop_purchases WHERE item_id = ?", item)).isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM user_items WHERE item_id = ?", item)).isEqualTo(1);
        assertThat(kit.total(a) + kit.total(b)).as("한 명만 500P 를 냈다").isEqualTo(1500);
        kit.assertInvariants(a, b);
    }

    @Test
    void 영구_아이템은_가지고_있으면_다시_살_수_없다() {
        long item = kit.color(300, "#E11D48");
        long member = kit.newMember();
        kit.grant(member, 1000);
        kit.buy(member, item);

        assertThatThrownBy(() -> kit.buy(member, item)).isInstanceOf(PointShopAlreadyOwnedException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("POINT_SHOP_ALREADY_OWNED");
        assertThat(kit.total(member)).isEqualTo(700);
        kit.assertInvariants(member);
    }

    @Test
    void 영구_아이템을_같은_회원이_동시에_두_번_사도_한_줄만_남는다_부분_유일_색인() throws Exception {
        long item = kit.color(300, "#2563EB");
        long member = kit.newMember();
        kit.grant(member, 1000);

        List<Callable<String>> jobs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            jobs.add(() -> {
                try {
                    kit.buy(member, item);
                    return "OK";
                } catch (PointShopAlreadyOwnedException e) {
                    return e.getErrorCode();
                }
            });
        }
        assertThat(PointShopTestKit.runTogether(jobs)).containsExactlyInAnyOrder("OK", "POINT_SHOP_ALREADY_OWNED");
        assertThat(kit.count("SELECT count(*) FROM user_items WHERE user_id = ? AND item_id = ?", member, item)).isEqualTo(1);
        assertThat(kit.total(member)).as("진 쪽은 포인트도 되돌렸다").isEqualTo(700);
        kit.assertInvariants(member);
    }

    @Test
    void 환불한_영구_아이템은_다시_살_수_있다() {
        long item = kit.color(300, "#16A34A");
        long member = kit.newMember();
        kit.grant(member, 1000);
        long first = kit.buyItem(member, item);
        kit.refundSelf(member, first);

        kit.buy(member, item);

        assertThat(kit.count("SELECT count(*) FROM user_items WHERE user_id = ? AND item_id = ?", member, item)).isEqualTo(2);
        assertThat(kit.total(member)).isEqualTo(700);
        kit.assertInvariants(member);
    }

    @Test
    void 판매_시작_전_판매_끝_사용_안_함_품절은_거절하고_아무것도_바뀌지_않는다() {
        long member = kit.newMember();
        kit.grant(member, 1000);
        Instant now = kit.clock.instant();
        long upcoming = Long.parseLong(kit.create(PointShopTestKit.request(kit.code("up"), PointShopItemKind.PROFILE_BORDER, PointShopUsageType.PERMANENT, 100, null, "NIGHT",
                null, null, now.plus(Duration.ofDays(3)), null, true)).id());
        long ended = Long.parseLong(kit.create(PointShopTestKit.request(kit.code("end"), PointShopItemKind.PROFILE_BORDER, PointShopUsageType.PERMANENT, 100, null, "NIGHT",
                null, null, now.minus(Duration.ofDays(3)), now.minus(Duration.ofSeconds(1)), true)).id());
        long inactive = Long.parseLong(kit.create(PointShopTestKit.request(kit.code("off"), PointShopItemKind.PROFILE_BORDER, PointShopUsageType.PERMANENT, 100, null,
                "NIGHT", null, null, null, null, false)).id());
        long soldOut = kit.border(100, "GOLD", 0);

        assertThatThrownBy(() -> kit.buy(member, upcoming)).isInstanceOf(PointShopNotOnSaleException.class);
        assertThatThrownBy(() -> kit.buy(member, ended)).isInstanceOf(PointShopNotOnSaleException.class);
        assertThatThrownBy(() -> kit.buy(member, inactive)).isInstanceOf(PointShopNotOnSaleException.class);
        assertThatThrownBy(() -> kit.buy(member, soldOut)).isInstanceOf(PointShopSoldOutException.class);
        assertThatThrownBy(() -> kit.buy(member, Long.MAX_VALUE)).isInstanceOf(PointShopItemNotFoundException.class);

        assertThat(kit.total(member)).isEqualTo(1000);
        assertThat(kit.count("SELECT count(*) FROM point_shop_purchases WHERE user_id = ?", member)).isZero();
        assertThat(kit.stock(soldOut)).isZero();

        kit.clock.set(now.plus(Duration.ofDays(4)));
        assertThat(kit.buy(member, upcoming).item().saleState()).as("시작 시각이 지나면 산다").isEqualTo(PointShopSaleState.ON_SALE);
    }

    @Test
    void 포인트_동결_회원은_살_수_없다() {
        long item = kit.color(100, "#9333EA");
        long member = kit.newMember();
        kit.grant(member, 1000);
        kit.freeze(member);

        assertThatThrownBy(() -> kit.buy(member, item)).isInstanceOf(MemberRestrictedException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("MEMBER_RESTRICTED");
        assertThat(kit.total(member)).isEqualTo(1000);
        assertThat(kit.count("SELECT count(*) FROM point_shop_purchases WHERE user_id = ?", member)).isZero();
    }

    @Test
    void 자격_정지_이메일_인증_전_회원_권한_없음_고지_동의_없음() {
        long item = kit.color(100, "#0EA5E9");
        long suspended = kit.newMember();
        kit.jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended);
        long unverified = kit.newMember();
        kit.jdbc.update("UPDATE users SET email_verified_at = NULL WHERE id = ?", unverified);
        long plain = kit.newMember();
        kit.grant(plain, 1000);

        assertThatThrownBy(() -> kit.buy(suspended, item)).isInstanceOf(PointShopBlockedException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("ACCOUNT_RESTRICTED");
        assertThatThrownBy(() -> kit.buy(unverified, item)).isInstanceOf(PointShopBlockedException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThatThrownBy(() -> kit.inTx(() -> kit.purchases.purchase(new PointShopBuyer(plain, false), item, true))).isInstanceOf(PointShopBlockedException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("PERMISSION_DENIED");
        assertThatThrownBy(() -> kit.inTx(() -> kit.purchases.purchase(new PointShopBuyer(plain, true), item, false))).isInstanceOf(ValidationException.class)
                .extracting(e -> ((CommonException) e).getFields().keySet()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.COLLECTION)
                .containsExactly("agreeRefundNotice");
        assertThatThrownBy(() -> kit.buy(Long.MAX_VALUE - 7, item)).isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("UNAUTHENTICATED");
        assertThat(kit.total(plain)).isEqualTo(1000);
    }

    @Test
    void 잔액이_모자라면_거절하고_재고_구매_원장이_그대로다() {
        long item = kit.border(800, "SILVER", 5);
        long member = kit.newMember();
        kit.grant(member, 500);
        int linesBefore = kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member);

        assertThatThrownBy(() -> kit.buy(member, item)).isInstanceOf(PointInsufficientException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("POINT_INSUFFICIENT");

        assertThat(kit.stock(item)).as("재고 차감도 되돌렸다").isEqualTo(5);
        assertThat(kit.count("SELECT count(*) FROM point_shop_purchases WHERE user_id = ?", member)).isZero();
        assertThat(kit.count("SELECT count(*) FROM user_items WHERE user_id = ?", member)).isZero();
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member)).isEqualTo(linesBefore);
        assertThat(kit.total(member)).isEqualTo(500);
        kit.assertInvariants(member);
    }

    @Test
    void 적립_포인트부터_빠지고_모자란_만큼_충전_포인트에서_빠진다_결정_9() {
        long item = kit.color(500, "#F59E0B");
        long member = kit.newMember();
        kit.chargePaid(member, 1000);
        kit.grant(member, 300);

        PointShopPurchaseResultView result = kit.buy(member, item);

        assertThat(result.balance()).isEqualTo(800);
        Map<String, Object> use = kit.jdbc.queryForMap("SELECT kind, reason, delta, free_delta, paid_delta, ref_type, ref_id FROM point_ledger WHERE user_id = ? AND reason = ?",
                member, PointReason.POINT_SHOP_PURCHASE.name());
        assertThat(use).containsEntry("kind", "USE").containsEntry("delta", -500).containsEntry("free_delta", -300L).containsEntry("paid_delta", -200L)
                .containsEntry("ref_type", PointService.REF_POINT_SHOP_PURCHASE).containsEntry("ref_id", Long.parseLong(result.purchaseId()));
        assertThat(kit.balance(member)).containsExactly(0, 800, 0, 800);
        kit.assertInvariants(member);
    }

    @Test
    void 구매_줄은_산_때의_이름_가격_사본이고_품목을_고쳐도_그대로다() {
        long item = kit.color(400, "#DC2626");
        long member = kit.newMember();
        kit.grant(member, 1000);
        PointShopPurchaseResultView result = kit.buy(member, item);
        long purchase = Long.parseLong(result.purchaseId());
        String code = kit.jdbc.queryForObject("SELECT code FROM point_shop_items WHERE id = ?", String.class, item);

        kit.inTx(() -> kit.catalog.update(item, new com.community.pointshop.dto.request.PointShopItemSaveRequest(code, "NICKNAME_COLOR",
                "PERMANENT", "새 이름", null, "바뀐 설명이에요.", null, "#DC2626", null, null, 900, null, null, null, null, true, 0), kit.operator, null));

        Map<String, Object> row = kit.purchaseRow(purchase);
        assertThat(row).containsEntry("price_point", 400).containsEntry("item_name", "시험 " + code).containsEntry("item_code", code)
                .containsEntry("item_kind", "NICKNAME_COLOR").containsEntry("usage_type", "PERMANENT").containsEntry("status", "COMPLETED");
        assertThat(row.get("refund_notice_agreed_at")).as("고지 동의 시각을 남긴다").isNotNull();
        var mine = kit.userItems.myItems(member).items().get(0);
        assertThat(mine.pricePaid()).isEqualTo(400);
        assertThat(mine.name()).as("내 아이템의 이름은 품목의 지금 값").isEqualTo("새 이름");
        assertThat(kit.refundSelf(member, Long.parseLong(result.userItemId()))).as("환불은 낸 값").isEqualTo(400);
        assertThat(kit.total(member)).isEqualTo(1000);
        kit.assertInvariants(member);
    }

    @Test
    void 같은_구매에_포인트를_두_번_쓸_수_없다() {
        long item = kit.color(100, "#65A30D");
        long member = kit.newMember();
        kit.grant(member, 1000);
        long purchase = Long.parseLong(kit.buy(member, item).purchaseId());

        assertThatThrownBy(() -> kit.inTx(() -> kit.points.use(member, 100, PointReason.POINT_SHOP_PURCHASE, PointService.REF_POINT_SHOP_PURCHASE, purchase)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(kit.total(member)).isEqualTo(900);
        kit.assertInvariants(member);
    }

    @Test
    void 구매_결과는_산_뒤의_잔액_재고_내_보유를_싣는다() {
        long ticket = kit.ticketItem(200);
        kit.jdbc.update("UPDATE point_shop_items SET stock = 3 WHERE id = ?", ticket);
        long member = kit.newMember();
        kit.grant(member, 1000);

        kit.buy(member, ticket);
        PointShopPurchaseResultView second = kit.buy(member, ticket);

        assertThat(second.balance()).isEqualTo(600);
        assertThat(second.item().stock()).isEqualTo(1);
        assertThat(second.item().mine().count()).as("소모형은 여러 장").isEqualTo(2);
        assertThat(second.item().mine().owned()).isFalse();
        assertThat(second.item().saleState()).isEqualTo(PointShopSaleState.ON_SALE);
    }

    @Test
    void 기간제는_보유_중에_또_사면_앞_끝에서_이어_붙고_기간이_끝나면_장착_해제로_보인다() {
        long item = kit.periodColor(100, 30);
        long member = kit.newMember();
        kit.grant(member, 1000);
        Instant now = kit.clock.instant();

        long first = kit.buyItem(member, item);
        kit.clock.set(now.plus(Duration.ofDays(10)));
        PointShopPurchaseResultView again = kit.buy(member, item);
        long second = Long.parseLong(again.userItemId());

        Map<String, Object> a = kit.userItemRow(first);
        Map<String, Object> b = kit.userItemRow(second);
        assertThat(((Timestamp) a.get("starts_at")).toInstant()).isEqualTo(now);
        assertThat(((Timestamp) a.get("expires_at")).toInstant()).isEqualTo(now.plus(Duration.ofDays(30)));
        assertThat(((Timestamp) b.get("starts_at")).toInstant()).as("앞 줄 끝에서 시작").isEqualTo(now.plus(Duration.ofDays(30)));
        assertThat(((Timestamp) b.get("expires_at")).toInstant()).isEqualTo(now.plus(Duration.ofDays(60)));
        assertThat(again.item().mine().periodEndsAt()).isEqualTo(now.plus(Duration.ofDays(60)));
        assertThat(kit.purchaseRow(Long.parseLong(again.purchaseId()))).containsEntry("duration_days", 30);

        kit.inTx(() -> kit.userItems.equip(member, com.community.pointshop.entity.EquipSlot.NICKNAME_COLOR, first));
        assertThat(kit.userItems.equipped(member).nicknameColor()).isNotNull();
        assertThat(kit.decorations.look(member).decoration().nicknameColor()).isEqualTo("#EA580C");

        kit.clock.set(now.plus(Duration.ofDays(30)));
        assertThat(kit.userItems.equipped(member).nicknameColor()).as("기간이 끝나면 배치 없이 읽을 때 해제").isNull();
        assertThat(kit.decorations.look(member).decoration()).as("작성자 장식도 빠진다").isNull();
        var rows = kit.userItems.myItems(member).items();
        var firstView = rows.stream().filter(r -> r.id().equals(Long.toString(first))).findFirst().orElseThrow();
        assertThat(firstView.expired()).isTrue();
        assertThat(firstView.equipped()).isFalse();
        assertThat(kit.userItemRow(first).get("status")).as("만료는 저장하지 않는다(결정 6)").isEqualTo("OWNED");
        kit.assertInvariants(member);
    }
}
