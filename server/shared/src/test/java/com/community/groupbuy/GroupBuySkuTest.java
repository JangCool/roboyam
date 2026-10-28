package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.address;
import static com.community.groupbuy.GroupBuyTestKit.buyer;
import static com.community.groupbuy.GroupBuyTestKit.lines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.groupbuy.dto.Availability;
import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.dto.JoinRequest;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.dto.StockChange;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.SkuKind;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyStore.SkuRow;
import com.community.groupbuy.service.ParticipationCancelService.CancelResult;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 옵션 축·단품(SKU)·추가 구성상품(계약 결정 88, G44) — 축 값 조합마다 MAIN 단품, 판매 끈 조합·단품별 한도·품절, 추가 구성상품 단독 구매 불가,
 * 공구 최대·성사 판정·1인 한도는 MAIN 수량만, 본 상품을 모두 취소할 때 추가상품 처리, 고시 양식은 추가상품마다 따로, 모집 뒤 구성 잠금.
 */
class GroupBuySkuTest {

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

    /** 색상(블랙·화이트) × 사이즈(S·M·L) — 블랙/L 판매 안 함, 화이트/M 추가금 2,000·한도 1 */
    private GroupBuyTestKit.Spec twoAxes(String policy) {
        return kit.spec(policy).axis("색상", "블랙", "화이트").axis("사이즈", "S", "M", "L")
                .combo(List.of("블랙", "L"), 0, null, false)
                .combo(List.of("화이트", "M"), 2_000, 1, true);
    }

    @Test
    void G44_축_2개면_값_조합마다_MAIN_단품이_생기고_상세에_축_값_단품이_실린다() {
        long gb = twoAxes(policy()).addon("전용 케이스", 5_000, 10).open();

        List<SkuRow> skus = kit.skus(gb);
        assertThat(skus.stream().filter(SkuRow::main).map(SkuRow::name))
                .containsExactly("블랙 / S", "블랙 / M", "블랙 / L", "화이트 / S", "화이트 / M", "화이트 / L");
        assertThat(skus.stream().filter(s -> !s.main())).singleElement().satisfies(s -> {
            assertThat(s.kind()).isEqualTo(SkuKind.ADDON);
            assertThat(s.name()).isEqualTo("전용 케이스");
            assertThat(s.unitPrice(25_000)).as("추가상품은 자기 가격").isEqualTo(5_000);
            assertThat(s.valueIds()).isEmpty();
        });
        SkuRow whiteM = kit.sku(gb, kit.skuId(gb, "화이트 / M"));
        assertThat(whiteM.unitPrice(25_000)).isEqualTo(27_000);
        assertThat(whiteM.maxQty()).isEqualTo(1);
        assertThat(kit.sku(gb, kit.skuId(gb, "블랙 / S")).maxQty()).as("설정을 적지 않은 조합 — 한도 없음·판매").isNull();
        assertThat(kit.sku(gb, kit.skuId(gb, "블랙 / L")).onSale()).isFalse();

        Availability a = kit.quotes.availability(gb);
        assertThat(a.axes()).extracting(Availability.Axis::name).containsExactly("색상", "사이즈");
        assertThat(a.axes().get(1).values()).extracting(Availability.Value::value).containsExactly("S", "M", "L");
        Availability.Sku detailWhiteM = a.skus().stream().filter(s -> s.name().equals("화이트 / M")).findFirst().orElseThrow();
        long white = a.axes().get(0).values().get(1).valueId();
        long m = a.axes().get(1).values().get(1).valueId();
        assertThat(detailWhiteM.valueIds()).as("축 차례대로 값 ID").containsExactly(white, m);
        assertThat(detailWhiteM.remaining()).as("min(단품 남은, 공구 남은)").isEqualTo(1);
        assertThat(detailWhiteM.unitPrice()).isEqualTo(27_000);
        assertThat(a.skus().stream().filter(s -> s.name().equals("블랙 / L")).findFirst().orElseThrow().onSale()).isFalse();
        assertThat(a.skus().stream().filter(s -> s.kind().equals("ADDON")).findFirst().orElseThrow().remaining()).isEqualTo(10);
    }

    @Test
    void G44_판매_끈_조합은_견적도_주문도_거절() {
        long gb = twoAxes(policy()).open();
        long blackL = kit.skuId(gb, "블랙 / L");
        assertThatThrownBy(() -> kit.quotes.quote(gb, lines(blackL, 1)))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.SKU_NOT_ON_SALE);
                    assertThat(e.getExtras()).containsEntry("skuId", Long.toString(blackL));
                });
        assertThatThrownBy(() -> kit.join.join(buyer(kit.newMember()), new JoinRequest(gb, lines(blackL, 1), address(), true, true, true, 28_000L)))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.SKU_NOT_ON_SALE));
        assertThat(kit.group(gb).reservedQty()).isZero();
    }

    @Test
    void G44_단품별_한도가_차면_그_단품만_품절이고_다른_조합은_계속_판다() {
        long gb = twoAxes(policy()).open();
        long whiteM = kit.skuId(gb, "화이트 / M");
        long blackS = kit.skuId(gb, "블랙 / S");
        JoinResult holding = kit.order(kit.newMember(), gb, lines(whiteM, 1));
        assertThat(skuOf(kit.quotes.availability(gb), whiteM).temporarilyUnavailable()).as("다른 분이 결제 중 — 잠시 구매 불가").isTrue();
        assertThatThrownBy(() -> kit.order(kit.newMember(), gb, lines(whiteM, 1)))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.SKU_SOLD_OUT);
                    assertThat(e.getExtras()).containsEntry("skuId", Long.toString(whiteM)).containsEntry("temporary", true);
                });
        kit.pay(holding);
        Availability.Sku sold = skuOf(kit.quotes.availability(gb), whiteM);
        assertThat(sold.soldOut()).isTrue();
        assertThat(sold.remaining()).isZero();
        assertThat(kit.group(gb).status()).as("다른 조합이 남았다 — 공구는 모집 중").isEqualTo(GroupBuyStatus.RECRUITING);
        kit.paidOrder(kit.newMember(), gb, lines(blackS, 3));
        assertThat(kit.sku(gb, blackS).currentQty()).isEqualTo(3);
        assertThat(kit.group(gb).currentQty()).isEqualTo(4);
        kit.assertInvariants(gb);
    }

    @Test
    void G44_추가_구성상품만_담으면_견적도_주문도_거절() {
        long gb = kit.spec(policy()).addon("보조 배터리", 9_000, null).open();
        long addon = kit.skuId(gb, "보조 배터리");
        assertThatThrownBy(() -> kit.quotes.quote(gb, lines(addon, 2)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("items"));
        assertThatThrownBy(() -> kit.join.join(buyer(kit.newMember()), new JoinRequest(gb, lines(addon, 2), address(), true, true, true, 21_000L)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("items"));
        assertThat(kit.sku(gb, addon).reservedQty()).isZero();

        JoinResult ok = kit.paidOrder(kit.newMember(), gb, lines(kit.mainSku(gb), 1, addon, 2));
        assertThat(ok.amount()).isEqualTo(25_000 + 18_000 + 3_000);
        kit.assertInvariants(gb);
    }

    @Test
    void G44_공구_최대와_1인_한도는_MAIN_수량만_세고_무료배송_기준에는_추가상품도_든다() {
        String policy = kit.policy(3_000, 50_000L, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).qty(0, 3).perUser(2).addon("케이블", 5_000, null).open();
        long main = kit.mainSku(gb);
        long cable = kit.skuId(gb, "케이블");
        long member = kit.newMember();

        assertThatThrownBy(() -> kit.order(member, gb, lines(main, 3)))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.LIMIT_EXCEEDED));
        JoinResult paid = kit.paidOrder(member, gb, lines(main, 1, cable, 5));
        assertThat(paid.amount()).as("25,000 + 5,000 × 5 = 50,000 — 추가상품까지 더해 무료배송").isEqualTo(50_000);
        assertThat(kit.group(gb).currentQty()).as("공구 수량은 MAIN 만").isEqualTo(1);
        assertThat(kit.sku(gb, cable).currentQty()).isEqualTo(5);

        JoinResult last = kit.paidOrder(kit.newMember(), gb, lines(main, 2, cable, 9));
        assertThat(last.amount()).isEqualTo(95_000);
        assertThat(kit.group(gb).currentQty()).isEqualTo(3);
        assertThat(kit.group(gb).status()).as("MAIN 3 = 최대 3 — 다 팔림").isEqualTo(GroupBuyStatus.CLOSING);
        kit.assertInvariants(gb);
    }

    @Test
    void G44_성사_판정은_MAIN_수량만_센다() {
        long gb = kit.spec(policy()).qty(3, 10).addon("케이블", 1_000, null)
                .period(kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofMinutes(10))).open();
        kit.paidOrder(kit.newMember(), gb, lines(kit.mainSku(gb), 2, kit.skuId(gb, "케이블"), 10));
        kit.advance(Duration.ofMinutes(10).plusSeconds(1));
        kit.runStateWorker();
        assertThat(kit.group(gb).status()).as("MAIN 2 < 최소 3 — 추가상품 10개는 세지 않는다").isEqualTo(GroupBuyStatus.CLOSED_FAILED);
        kit.runRefundWorker();
        kit.assertInvariants(gb);
    }

    @Test
    void G44_본_상품을_모두_취소하면서_추가상품만_남기면_함께_취소하라고_막는다() {
        long gb = kit.spec(policy()).option("빨강", 0, null).option("파랑", 0, null).addon("케이블", 5_000, null).open();
        long red = kit.skuId(gb, "빨강");
        long blue = kit.skuId(gb, "파랑");
        long cable = kit.skuId(gb, "케이블");
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, lines(red, 1, blue, 1, cable, 2));

        CancelResult partial = kit.cancels.cancelByMember(member, paid.participationId(), lines(red, 1));
        assertThat(partial.wholeOrder()).as("본 상품이 남으면 추가상품은 그대로").isFalse();
        assertThatThrownBy(() -> kit.cancels.cancelByMember(member, paid.participationId(), lines(blue, 1)))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.ADDON_ONLY);
                    assertThat(e.getExtras()).containsEntry("skuIds", List.of(Long.toString(cable)));
                });
        assertThat(kit.sku(gb, blue).currentQty()).as("거절이면 아무것도 바뀌지 않는다").isEqualTo(1);

        CancelResult rest = kit.cancels.cancelByMember(member, paid.participationId(), lines(blue, 1, cable, 2));
        assertThat(rest.wholeOrder()).isTrue();
        assertThat(rest.status()).isEqualTo(ParticipationStatus.CANCELED);
        assertThat(kit.sku(gb, cable).currentQty()).isZero();
        assertThat(kit.group(gb).currentQty()).isZero();
        kit.assertInvariants(gb);
    }

    @Test
    void G44_고시_양식은_추가상품마다_따로_필수이고_MAIN_과_한_양식에_섞을_수_없다() {
        long gb = kit.spec(policy()).addon("충전기", 9_000, null).addon("케이스", 3_000, null).create();
        kit.notices.save(gb, List.of(kit.fullNotice("OTHER_GOODS", "본체", List.of()), kit.fullNotice("SMALL_ELECTRONICS", "충전기", List.of("충전기"))),
                kit.operator, "ip");
        assertThatThrownBy(() -> kit.admin.publish(gb, kit.group(gb).version(), kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.NOTICE_INCOMPLETE);
                    assertThat(e.getExtras()).as("기본 양식은 추가상품을 덮지 않는다").containsEntry("unassignedSkus", List.of("케이스"));
                });
        assertThatThrownBy(() -> kit.notices.save(gb, List.of(kit.fullNotice("OTHER_GOODS", "본체", List.of()),
                kit.fullNotice("SMALL_ELECTRONICS", "부속", List.of("충전기", "케이스"))), kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("notices[1].skuNames"));
        assertThatThrownBy(() -> kit.notices.save(gb, List.of(kit.fullNotice("OTHER_GOODS", "본체", List.of("시험 상품", "케이스"))), kit.operator, "ip"))
                .as("MAIN 과 추가상품을 한 양식에").isInstanceOf(ValidationException.class);

        kit.fillNotice(gb);
        kit.admin.publish(gb, kit.group(gb).version(), kit.operator, "ip");
        assertThat(kit.group(gb).status()).isEqualTo(GroupBuyStatus.RECRUITING);
        assertThat(kit.notices.notices(gb)).hasSize(3);
    }

    @Test
    void G44_모집_뒤에는_축_조합_추가상품_구성을_못_바꾸고_추가상품_입고는_공구_최대를_움직이지_않는다() {
        GroupBuyTestKit.Spec spec = kit.spec(policy()).qty(0, 10).axis("색상", "블랙", "화이트").addon("케이블", 5_000, 5);
        long gb = spec.open();
        GroupBuyDraft o = spec.draft();
        List<GroupBuyDraft.AddonDraft> pricier = List.of(new GroupBuyDraft.AddonDraft("케이블", 6_000, 5, null));
        List<GroupBuyDraft.SkuDraft> offWhite = List.of(new GroupBuyDraft.SkuDraft(List.of("화이트"), 0, null, false));
        List<GroupBuyDraft.AxisDraft> moreValues = List.of(new GroupBuyDraft.AxisDraft("색상", List.of("블랙", "화이트", "레드")));
        for (GroupBuyDraft changed : List.of(withOptions(o, o.axes(), o.skus(), pricier), withOptions(o, o.axes(), offWhite, o.addons()),
                withOptions(o, moreValues, o.skus(), o.addons()))) {
            assertThatThrownBy(() -> kit.admin.update(gb, kit.group(gb).version(), changed, kit.operator, "ip"))
                    .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.LOCKED_FIELD);
                        assertThat(e.getExtras()).containsEntry("field", "options");
                    });
        }
        kit.admin.update(gb, kit.group(gb).version(), withOptions(o, o.axes(), o.skus(), o.addons()), kit.operator, "ip"); // 같은 구성이면 통과

        long cable = kit.skuId(gb, "케이블");
        StockChange added = kit.admin.addStock(gb, cable, 3, "케이블 입고", kit.operator, "ip");
        assertThat(added.skuMaxQty()).isEqualTo(8);
        assertThat(added.maxQty()).as("추가상품 입고는 공구 최대와 무관").isEqualTo(10);
        assertThat(kit.group(gb).addedQty()).isZero();
        StockChange reduced = kit.admin.reduceStock(gb, cable, 2, "케이블 불량", kit.operator, "ip");
        assertThat(reduced.skuMaxQty()).isEqualTo(6);
        assertThat(kit.group(gb).maxQty()).isEqualTo(10);
        kit.assertInvariants(gb);
    }

    @Test
    void G44_축_조합_추가상품_등록_검사() {
        String policy = policy();
        assertThatThrownBy(() -> kit.spec(policy).axis("a", "1").axis("b", "1").axis("c", "1").axis("d", "1").create())
                .as("축은 3개까지").isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("axes"));
        String[] many = new String[15];
        for (int i = 0; i < many.length; i++) {
            many[i] = "v" + i;
        }
        assertThatThrownBy(() -> kit.spec(policy).axis("a", many).axis("b", many).create())
                .as("조합 225 > 200").isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("axes"));
        assertThatThrownBy(() -> kit.spec(policy).axis("a", "x", "x").create()).as("값 중복").isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> kit.spec(policy).axis("a", "x").combo(List.of("y"), 0, null, true).create())
                .as("없는 조합").isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("skus"));
        assertThatThrownBy(() -> kit.spec(policy).axis("a", "x").combo(List.of("x"), 0, null, false).create())
                .as("판매하는 조합이 없다").isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("skus"));
        assertThatThrownBy(() -> kit.spec(policy).axis("a", "x").addon("x", 1_000, null).create())
                .as("추가상품 이름이 조합 이름과 같다").isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("addons"));
        assertThatThrownBy(() -> kit.spec(policy).addon("y", 0, null).create()).as("추가상품 가격 0").isInstanceOf(ValidationException.class);

        long plain = kit.spec(policy).mainMax(4).create();
        assertThat(kit.skus(plain)).singleElement().satisfies(s -> {
            assertThat(s.name()).as("축이 없으면 MAIN 단품 하나(이름 = 상품 이름)").isEqualTo("시험 상품");
            assertThat(s.maxQty()).isEqualTo(4);
        });
        assertThat(kit.count("SELECT count(*) FROM group_buy_option_axes WHERE group_buy_id = ?", plain)).isZero();
    }

    private static Availability.Sku skuOf(Availability a, long skuId) {
        return a.skus().stream().filter(s -> s.skuId() == skuId).findFirst().orElseThrow();
    }

    private static GroupBuyDraft withOptions(GroupBuyDraft o, List<GroupBuyDraft.AxisDraft> axes, List<GroupBuyDraft.SkuDraft> skus,
                                             List<GroupBuyDraft.AddonDraft> addons) {
        return new GroupBuyDraft(o.title(), o.productName(), o.summary(), o.spec(), o.description(), o.normalPrice(), o.normalPriceBasis(), o.normalPriceSource(),
                o.groupPrice(), o.shippingPolicyCode(), o.minQty(), o.maxQty(), o.maxQtyPerUser(), o.startAt(), o.endAt(), o.extensionMode(), o.fulfillment(),
                o.shipPlannedAt(), o.shipWithinBusinessDays(), o.withdrawalRestriction(), o.returnPolicy(), o.roundLimitShared(), new ArrayList<>(axes),
                new ArrayList<>(skus), new ArrayList<>(addons), o.images());
    }
}
