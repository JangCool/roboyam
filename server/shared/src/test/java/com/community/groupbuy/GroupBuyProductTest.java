package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.dto.Availability;
import com.community.groupbuy.dto.ProductDraft;
import com.community.groupbuy.dto.Quote;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.service.GroupBuyProductService;
import com.community.zconfiguration.exception.ValidationException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 상품 마스터·카테고리·공급가(계약 결정 89, G45) — 상품 하나로 공구 둘, 모집 시작 사본, 모집 중 상품 단품 구성 잠금, 공급가 빈 값 거절·0 허용·역마진 확인,
 * 공급가는 회원 응답에 없음, 카테고리 필터(2단).
 */
class GroupBuyProductTest {

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
    void G45_상품_하나로_공구_둘을_열고_단품_수량은_공구마다_따로() {
        GroupBuyTestKit.Spec first = kit.spec(policy()).option("빨강", 0, 3).option("파랑", 0, 3);
        long a = first.open();
        long product = kit.productOf(a);
        long b = kit.spec(policy()).product(product).price(23_000).combo(List.of("빨강"), 0, 2, true).open();

        assertThat(kit.productOf(b)).isEqualTo(product);
        assertThat(kit.skus(a)).extracting(s -> s.productSkuId()).containsExactlyElementsOf(kit.skus(b).stream().map(s -> s.productSkuId()).toList());
        assertThat(kit.skuId(a, "빨강")).isNotEqualTo(kit.skuId(b, "빨강"));
        kit.paidOrder(kit.newMember(), a, lines(kit.skuId(a, "빨강"), 3));
        kit.paidOrder(kit.newMember(), b, lines(kit.skuId(b, "빨강"), 1));
        assertThat(kit.sku(a, kit.skuId(a, "빨강")).currentQty()).isEqualTo(3);
        assertThat(kit.sku(b, kit.skuId(b, "빨강")).currentQty()).isEqualTo(1);
        assertThat(kit.sku(b, kit.skuId(b, "빨강")).maxQty()).isEqualTo(2);
        assertThat(kit.quotes.quote(b, lines(kit.skuId(b, "파랑"), 1)).itemsAmount()).as("공구마다 판매 조건").isEqualTo(23_000);
        kit.assertInvariants(a, b);
    }

    @Test
    void G45_모집_시작_때_상품_내용의_사본을_공구_수정_이력에_남긴다() {
        long gb = kit.spec(policy()).axis("색상", "블랙", "화이트").open();
        assertThat(kit.count("SELECT count(*) FROM group_buy_revisions WHERE group_buy_id = ? AND revision_no = 1 AND change_kind = 'START' "
                + "AND product_name = '시험 상품' AND description = '<p>설명</p>' AND images @> '[{\"imageKey\":\"img/a.jpg\"}]' "
                + "AND option_axes -> 0 ->> 'name' = '색상' AND jsonb_array_length(skus) = 2 AND skus -> 0 ->> 'name' = '블랙' "
                + "AND jsonb_array_length(notices) = 1 AND NOT jsonb_exists(skus -> 0, 'supplyPrice')", gb)).isEqualTo(1);
    }

    @Test
    void G45_모집_중인_공구가_쓰는_상품은_이름_단품_구성이_잠기고_설명은_고칠_수_있다() {
        GroupBuyTestKit.Spec spec = kit.spec(policy()).axis("색상", "블랙", "화이트").addon("케이블", 5_000, null);
        long gb = spec.open();
        long product = kit.productOf(gb);
        long addonProduct = kit.productsOf(gb).get(1);

        ProductDraft p = spec.productDraft();
        List<ProductDraft.SkuDraft> withRed = new java.util.ArrayList<>(p.skus());
        withRed.add(new ProductDraft.SkuDraft(null, "레드", List.of("레드"), 10_000L, true));
        ProductDraft moreValues = withSkus(p, p.optionTypeCodes(), withRed);
        assertThatThrownBy(() -> kit.products.update(product, version(product), moreValues, kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.LOCKED_FIELD);
                    assertThat(e.getExtras()).containsEntry("field", "options");
                });
        ProductDraft renamed = new ProductDraft(p.categoryId(), "새 이름", p.summary(), p.spec(), p.description(), null, null, null, null, p.optionTypeCodes(), p.skus(),
                p.images());
        assertThatThrownBy(() -> kit.products.update(product, version(product), renamed, kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getExtras()).containsEntry("field", "name"));
        long cableSku = kit.products.skus(addonProduct).getFirst().id();
        ProductDraft addon = new ProductDraft(kit.category, "케이블", null, null, null, null, null, null, null, List.of(kit.optionType("길이")),
                List.of(new ProductDraft.SkuDraft(cableSku, "1m", List.of("1m"), 2_500L, true), new ProductDraft.SkuDraft(null, "2m", List.of("2m"), 3_000L, true)), List.of());
        assertThatThrownBy(() -> kit.products.update(addonProduct, version(addonProduct), addon, kit.operator, "ip"))
                .as("추가 구성상품으로 쓰여도 잠긴다").isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.LOCKED_FIELD));

        ProductDraft described = new ProductDraft(p.categoryId(), p.name(), p.summary(), p.spec(), "<p>정정</p>", null, null, null, null, p.optionTypeCodes(), p.skus(),
                p.images());
        kit.products.update(product, version(product), described, kit.operator, "ip");
        assertThat(kit.group(gb).description()).isEqualTo("<p>정정</p>");

        // 모집이 끝나면(중단) 구성을 바꿀 수 있다 — 새 단품은 다음 공구부터, 공구가 가리키는 단품은 지울 수 없다
        kit.admin.cancel(gb, "시험 중단", kit.operator, "ip");
        kit.products.update(product, version(product), withSkus(described, described.optionTypeCodes(), withRed), kit.operator, "ip");
        assertThat(kit.products.skus(product)).extracting(s -> s.name()).containsExactly("블랙", "화이트", "레드");
        assertThat(kit.skus(gb)).filteredOn(s -> s.main()).hasSize(2);
        long red = kit.products.skus(product).getLast().id();
        assertThatThrownBy(() -> kit.products.update(product, version(product), withSkus(described, described.optionTypeCodes(),
                List.of(new ProductDraft.SkuDraft(red, "레드", List.of("레드"), 10_000L, true))), kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.PRODUCT_SKU_IN_USE));
    }

    @Test
    void G45_공급가가_빈_단품이_있으면_모집_시작_거절_0은_허용_역마진은_확인해야_진행() {
        GroupBuyTestKit.Spec spec = kit.spec(policy()).supply(null);
        long missing = spec.create();
        kit.fillNotice(missing);
        assertThatThrownBy(() -> kit.admin.publish(missing, kit.group(missing).version(), kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.SUPPLY_PRICE_MISSING);
                    assertThat(e.getExtras()).containsEntry("skuIds", List.of(Long.toString(kit.mainSku(missing))));
                });
        assertThat(kit.group(missing).publishedAt()).isNull();

        spec.comboSupply(List.of(), 0L); // 0 은 명시 입력
        kit.admin.update(missing, kit.group(missing).version(), spec.draft(), kit.operator, "ip");
        kit.admin.publish(missing, kit.group(missing).version(), kit.operator, "ip");
        assertThat(kit.group(missing).status()).isEqualTo(GroupBuyStatus.RECRUITING);
        assertThat(kit.sku(missing, kit.mainSku(missing)).supplyPrice()).isZero();

        long negative = kit.spec(policy()).supply(30_000L).create();
        kit.fillNotice(negative);
        assertThatThrownBy(() -> kit.admin.publish(negative, kit.group(negative).version(), kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.NEGATIVE_MARGIN));
        kit.admin.publish(negative, kit.group(negative).version(), true, kit.operator, "ip");
        assertThat(kit.group(negative).status()).isEqualTo(GroupBuyStatus.RECRUITING);
        assertThat(kit.audits).anySatisfy(a -> {
            assertThat(a.action()).isEqualTo(AuditActions.GROUPBUY_START);
            assertThat(a.targetId()).isEqualTo(Long.toString(negative));
            assertThat(a.detail()).containsEntry("negativeMarginSkus", List.of(Long.toString(kit.mainSku(negative))));
        });
    }

    @Test
    void G45_공급가는_회원_응답에_없다() {
        for (Class<?> type : List.of(Availability.class, Availability.Sku.class, Availability.Axis.class, Quote.class, Quote.Line.class)) {
            assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName)).as(type.getSimpleName())
                    .noneMatch(name -> name.toLowerCase().contains("supply"));
        }
    }

    @Test
    void G45_카테고리는_2단이고_회원_목록은_카테고리와_그_아래_단으로_거르며_숨긴_카테고리는_뺀다() {
        long top = kit.products.createCategory("농산물 " + kit.ids.nextId(), null, 0, kit.operator, "ip");
        long rice = kit.products.createCategory("쌀", top, 0, kit.operator, "ip");
        long fruit = kit.products.createCategory("과일", top, 1, kit.operator, "ip");
        long hidden = kit.products.createCategory("숨김", top, 2, kit.operator, "ip");
        assertThatThrownBy(() -> kit.products.createCategory("3단", rice, 0, kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("parentId"));
        kit.products.updateCategory(hidden, "숨김", 2, true, kit.operator, "ip");

        long riceGb = kit.spec(policy()).category(rice).open();
        long fruitGb = kit.spec(policy()).category(fruit).open();
        long hiddenGb = kit.spec(policy()).category(hidden).open();
        long draft = kit.spec(policy()).category(rice).create();

        assertThat(kit.products.publicList(top, 100)).containsExactlyInAnyOrder(riceGb, fruitGb);
        assertThat(kit.products.publicList(rice, 100)).containsExactly(riceGb).doesNotContain(draft);
        assertThat(kit.products.publicList(hidden, 100)).isEmpty();
        assertThat(kit.products.publicList(null, 100)).doesNotContain(hiddenGb, draft);
        assertThatThrownBy(() -> kit.products.create(new ProductDraft(null, "카테고리 없음", null, null, null, null, null, null, null, List.of(), List.of(), List.of()),
                kit.operator, "ip")).isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("categoryId"));
    }

    @Test
    void 상품_옵션은_옵션_종류_코드로_고르고_이름을_바꾸면_따라가며_숨긴_종류는_새로_못_고르고_쓰는_종류는_못_지운다() {
        String code = "MAT" + Long.toString(kit.ids.nextId(), 36).toUpperCase();
        kit.products.saveOptionType(new GroupBuyProductService.OptionTypeDraft(code, "소재 " + code, 5, false), kit.operator, "ip");
        long product = kit.products.create(new ProductDraft(kit.category, "가방", null, null, null, null, null, null, null, List.of(code),
                List.of(new ProductDraft.SkuDraft(null, "가죽", List.of("가죽"), null, true), new ProductDraft.SkuDraft(null, "캔버스", List.of("캔버스"), null, true)), List.of()),
                kit.operator, "ip");
        assertThat(kit.products.optionTypes()).filteredOn(t -> t.code().equals(code)).singleElement().satisfies(t -> {
            assertThat(t.productCount()).isEqualTo(1);
            assertThat(t.usedValues()).containsExactlyInAnyOrder("가죽", "캔버스");
        });

        kit.products.saveOptionType(new GroupBuyProductService.OptionTypeDraft(code, "재질 " + code, 5, true), kit.operator, "ip"); // 이름 바꾸고 숨김
        assertThat(kit.products.axes(product).getFirst().name()).as("이름은 종류를 따라간다").isEqualTo("재질 " + code);
        assertThat(kit.products.axes(product).getFirst().code()).isEqualTo(code);
        assertThatThrownBy(() -> kit.products.create(new ProductDraft(kit.category, "새 가방", null, null, null, null, null, null, null, List.of(code),
                List.of(new ProductDraft.SkuDraft(null, "가죽", List.of("가죽"), null, true)), List.of()), kit.operator, "ip"))
                .as("숨긴 종류는 새로 못 고른다").isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("optionTypeCodes"));
        var skus = kit.products.skus(product);
        kit.products.update(product, version(product), new ProductDraft(kit.category, "가방", null, null, null, null, null, null, null, List.of(code),
                List.of(new ProductDraft.SkuDraft(skus.get(0).id(), "가죽", List.of("가죽"), 1_000L, true), new ProductDraft.SkuDraft(skus.get(1).id(), "캔버스",
                        List.of("캔버스"), null, true)), List.of()), kit.operator, "ip"); // 이미 쓰는 숨긴 종류는 그대로 둘 수 있다
        assertThatThrownBy(() -> kit.products.create(new ProductDraft(kit.category, "x", null, null, null, null, null, null, null, List.of("NOPE_" + code.substring(3)),
                List.of(new ProductDraft.SkuDraft(null, "a", List.of("a"), null, true)), List.of()), kit.operator, "ip"))
                .as("없는 코드").isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("optionTypeCodes"));
        assertThatThrownBy(() -> kit.products.saveOptionType(new GroupBuyProductService.OptionTypeDraft("ab", "소문자", 0, false), kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("optionTypes"));

        // 목록 통째 저장(PUT)에서 쓰는 종류를 빼면 409 — 아무것도 지우지 않는다
        List<GroupBuyProductService.OptionTypeDraft> withoutMine = kit.products.optionTypes().stream().filter(t -> !t.code().equals(code))
                .map(t -> new GroupBuyProductService.OptionTypeDraft(t.code(), t.name(), t.sortOrder(), t.hidden())).toList();
        assertThatThrownBy(() -> kit.products.saveOptionTypes(withoutMine, kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.OPTION_TYPE_IN_USE);
                    assertThat(e.getExtras()).containsEntry("codes", List.of(code));
                });
        assertThat(kit.products.optionTypes()).anySatisfy(t -> assertThat(t.code()).isEqualTo(code));
    }

    private static int version(long productId) {
        return kit.products.product(productId).version();
    }

    private static ProductDraft withSkus(ProductDraft p, List<String> optionTypeCodes, List<ProductDraft.SkuDraft> skus) {
        return new ProductDraft(p.categoryId(), p.name(), p.summary(), p.spec(), p.description(), p.normalPrice(), p.normalPriceBasis(), p.normalPriceSource(),
                p.withdrawalRestriction(), optionTypeCodes, skus, p.images());
    }

    @Test
    void 상품_단품_목록이_정본이고_서버가_값으로_옵션_축을_만든다() {
        long product = kit.products.create(new ProductDraft(kit.category, "티셔츠", null, null, null, null, null, null, null, List.of(kit.optionType("색상"), kit.optionType("사이즈")),
                List.of(new ProductDraft.SkuDraft(null, "블랙 M", List.of("블랙", "M"), 5_000L, true), new ProductDraft.SkuDraft(null, "화이트 L", List.of("화이트", "L"), null,
                        true), new ProductDraft.SkuDraft(null, "블랙 L", List.of("블랙", "L"), 5_000L, false)), List.of()), kit.operator, "ip");
        var axes = kit.products.axes(product);
        assertThat(axes).extracting(a -> a.name()).containsExactly("색상", "사이즈");
        assertThat(axes.get(0).values()).extracting(v -> v.value()).as("처음 나온 차례").containsExactly("블랙", "화이트");
        assertThat(axes.get(1).values()).extracting(v -> v.value()).containsExactly("M", "L");
        var skus = kit.products.skus(product);
        assertThat(skus).extracting(s -> s.name()).containsExactly("블랙 M", "화이트 L", "블랙 L");
        assertThat(skus.get(1).valueIds()).containsExactly(axes.get(0).values().get(1).id(), axes.get(1).values().get(1).id());

        // 이름 바꾸기·값 바꾸기는 같은 단품 ID 로, 맞바꿔도 된다
        long blackM = skus.get(0).id();
        long whiteL = skus.get(1).id();
        kit.products.update(product, version(product), new ProductDraft(kit.category, "티셔츠", null, null, null, null, null, null, null, List.of(kit.optionType("색상"), kit.optionType("사이즈")),
                List.of(new ProductDraft.SkuDraft(blackM, "화이트 L", List.of("화이트", "L"), 5_000L, true), new ProductDraft.SkuDraft(whiteL, "블랙 M", List.of("블랙", "M"),
                        null, true)), List.of()), kit.operator, "ip");
        assertThat(kit.products.skus(product)).extracting(s -> s.id() + "=" + s.name()).containsExactly(blackM + "=화이트 L", whiteL + "=블랙 M");

        // 옵션 없는 상품은 이름으로 가리는 단품 여럿도 된다
        long plain = kit.products.create(new ProductDraft(kit.category, "쌀", null, null, null, null, null, null, null, List.of(),
                List.of(new ProductDraft.SkuDraft(null, "쌀 10kg", List.of(), 30_000L, true), new ProductDraft.SkuDraft(null, "쌀 20kg", List.of(), 55_000L, true)), List.of()),
                kit.operator, "ip");
        assertThat(kit.products.axes(plain)).isEmpty();
        assertThat(kit.products.skus(plain)).hasSize(2);

        // 검사: 단품 없음 · 이름 겹침 · 옵션이 있는데 값 빠짐 · 같은 값 묶음 두 줄 · 옵션 이름 4개 · 다른 상품의 단품 ID
        for (ProductDraft bad : List.of(
                new ProductDraft(kit.category, "x", null, null, null, null, null, null, null, List.of(), List.of(), List.of()),
                new ProductDraft(kit.category, "x", null, null, null, null, null, null, null, List.of(), List.of(new ProductDraft.SkuDraft(null, "a", List.of(), null, true),
                        new ProductDraft.SkuDraft(null, "a", List.of(), null, true)), List.of()),
                new ProductDraft(kit.category, "x", null, null, null, null, null, null, null, List.of(kit.optionType("색상")), List.of(new ProductDraft.SkuDraft(null, "a", List.of(), null, true)),
                        List.of()),
                new ProductDraft(kit.category, "x", null, null, null, null, null, null, null, List.of(kit.optionType("색상")), List.of(new ProductDraft.SkuDraft(null, "a", List.of("블랙"), null,
                        true), new ProductDraft.SkuDraft(null, "b", List.of("블랙"), null, true)), List.of()))) {
            assertThatThrownBy(() -> kit.products.create(bad, kit.operator, "ip"))
                    .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("skus"));
        }
        assertThatThrownBy(() -> kit.products.create(new ProductDraft(kit.category, "x", null, null, null, null, null, null, null, List.of(kit.optionType("a"), kit.optionType("b"), kit.optionType("c"), kit.optionType("d")),
                List.of(new ProductDraft.SkuDraft(null, "a", List.of("1", "1", "1", "1"), null, true)), List.of()), kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("optionTypeCodes"));
        assertThatThrownBy(() -> kit.products.update(plain, version(plain), new ProductDraft(kit.category, "쌀", null, null, null, null, null, null, null, List.of(),
                List.of(new ProductDraft.SkuDraft(blackM, "남의 단품", List.of(), null, true)), List.of()), kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("skus"));
    }
}
