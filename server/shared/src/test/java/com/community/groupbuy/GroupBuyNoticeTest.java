package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.groupbuy.dto.NoticeDraft;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.service.GroupBuyNoticeCatalog;
import com.community.groupbuy.service.GroupBuyNoticeService;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 상품정보제공고시(계약 결정 87, G43) — 카탈로그(공정위 고시 제2022-15호 Ⅲ-1 40개 품목), 양식 저장·완성 검사(일반원칙 ③), 옵션 소속(일반원칙 ⑦), 모집 뒤 수정 이력, 추가 차수 복사·카탈로그 버전 고정.
 */
class GroupBuyNoticeTest {

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

    private void publish(long id) {
        kit.admin.publish(id, kit.group(id).version(), kit.operator, "ip");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> incompleteNotices(GroupBuyException e) {
        return (List<Map<String, Object>>) e.getExtras().get("notices");
    }

    // ── 카탈로그 ────────────────────────────────────────────────────────────

    @Test
    void 카탈로그는_2023_01_01_시행_고시의_40개_품목이고_항목은_품목_안에서_겹치지_않는다() {
        GroupBuyNoticeCatalog.Version current = kit.catalog.current();
        assertThat(current.version()).isEqualTo("2023-01-01");
        assertThat(current.noticeNo()).isEqualTo("공정거래위원회고시 제2022-15호");
        assertThat(current.source()).contains("law.go.kr");
        assertThat(current.categories()).hasSize(40);
        assertThat(current.categories()).extracting(GroupBuyNoticeCatalog.Category::no).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 40).boxed().toList());
        assertThat(current.categories().get(38).code()).isEqualTo("OTHER_SERVICES");
        assertThat(current.categories().get(39).code()).isEqualTo("OTHER_GOODS");
        for (GroupBuyNoticeCatalog.Category c : current.categories()) {
            Set<String> keys = new HashSet<>();
            assertThat(c.items()).as(c.code()).isNotEmpty().allSatisfy(i -> {
                assertThat(keys.add(i.key())).as(c.code() + " 키 겹침 " + i.key()).isTrue();
                assertThat(i.key()).matches("[a-z][a-z0-9_]{0,59}");
                assertThat(i.name()).isNotBlank();
                assertThat(i.required()).as("일반원칙 ③ — 모든 항목은 값 또는 제공 불가 사유").isTrue();
            });
        }
        assertThat(current.principles()).containsKeys("unavailableReason", "dateAlternatives", "qualityWarranty", "kcCert", "accessory");
        // 원칙 ④⑤⑥ 이 붙는 항목 표시
        assertThat(current.category("HOME_APPLIANCES").orElseThrow().item("kc_certification").orElseThrow().kind()).isEqualTo(GroupBuyNoticeCatalog.ItemKind.KC_CERT);
        assertThat(current.category("CLOTHING").orElseThrow().item("manufactured_month").orElseThrow().kind()).isEqualTo(GroupBuyNoticeCatalog.ItemKind.DATE_LIKE);
        assertThat(current.category("CLOTHING").orElseThrow().item("quality_warranty").orElseThrow().kind())
                .isEqualTo(GroupBuyNoticeCatalog.ItemKind.QUALITY_WARRANTY);
        assertThat(current.category("MOBILE_DEVICES").orElseThrow().warrantyDisadvantageDisclosure()).isNotBlank();
        assertThat(kit.catalog.versions()).contains("2022-01-01", "2023-01-01");
    }

    // ── G43 ─────────────────────────────────────────────────────────────────

    @Test
    void G43_양식이_없거나_항목이_빈_채_모집_시작하면_빈_양식과_항목을_돌려주며_거절() {
        long none = kit.spec(policy()).create();
        assertThatThrownBy(() -> publish(none)).isInstanceOfSatisfying(GroupBuyException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.NOTICE_INCOMPLETE);
            assertThat(e.getExtras()).containsEntry("noNotice", true);
        });

        long partial = kit.spec(policy()).create();
        NoticeDraft full = kit.fullNotice("CLOTHING", "티셔츠", List.of());
        List<NoticeDraft.Value> missingTwo = full.values().stream().filter(v -> !v.itemKey().equals("color") && !v.itemKey().equals("size")).toList();
        kit.notices.save(kit.productOf(partial), List.of(new NoticeDraft("CLOTHING", "티셔츠", List.of(), missingTwo)), kit.operator, "ip");
        assertThatThrownBy(() -> publish(partial)).isInstanceOfSatisfying(GroupBuyException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.NOTICE_INCOMPLETE);
            assertThat(incompleteNotices(e)).singleElement().satisfies(n -> {
                assertThat(n).containsEntry("title", "티셔츠").containsEntry("categoryCode", "CLOTHING").containsEntry("index", 0);
                assertThat((List<String>) n.get("missingKeys")).containsExactlyInAnyOrder("color", "size");
            });
        });
        assertThat(kit.group(partial).publishedAt()).isNull();

        // 빈 문자열 값도 빈 항목이다
        List<NoticeDraft.Value> blank = new ArrayList<>(missingTwo);
        blank.add(new NoticeDraft.Value("color", "  ", false, null));
        blank.add(new NoticeDraft.Value("size", "FREE", false, null));
        kit.notices.save(kit.productOf(partial), List.of(new NoticeDraft("CLOTHING", "티셔츠", List.of(), blank)), kit.operator, "ip");
        assertThatThrownBy(() -> publish(partial)).isInstanceOf(GroupBuyException.class);

        // 「제공 불가 + 사유」는 채운 것으로 본다
        List<NoticeDraft.Value> filled = new ArrayList<>(missingTwo);
        filled.add(new NoticeDraft.Value("color", null, true, "단색 상품이 아니라 받는 색이 무작위로 정해집니다"));
        filled.add(new NoticeDraft.Value("size", "FREE", false, null));
        kit.notices.save(kit.productOf(partial), List.of(new NoticeDraft("CLOTHING", "티셔츠", List.of(), filled)), kit.operator, "ip");
        publish(partial);
        assertThat(kit.group(partial).status()).isEqualTo(GroupBuyStatus.RECRUITING);
    }

    @Test
    void G43_제공_불가는_사유가_없으면_저장_거절_DB_도_막는다() {
        long gb = kit.spec(policy()).create();
        assertThatThrownBy(() -> kit.notices.save(kit.productOf(gb), List.of(new NoticeDraft("OTHER_GOODS", "본체", List.of(),
                List.of(new NoticeDraft.Value("product_model_name", null, true, " ")))), kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("notices[0].values.product_model_name"));
        kit.fillNotice(gb);
        long noticeId = kit.notices.notices(kit.productOf(gb)).get(0).id();
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE group_buy_notice_values SET unavailable = true, value = NULL, unavailable_reason = NULL WHERE notice_id = ? "
                + "AND item_key = 'product_model_name'", noticeId)).hasMessageContaining("ck_gbnv_unavailable");
    }

    @Test
    void G43_어느_양식에도_속하지_않은_단품이_있으면_거절_기본_양식이_있으면_나머지_MAIN_을_덮는다() {
        long gb = kit.spec(policy()).option("본체", 0, 10).option("충전기", 5_000, 10).option("케이스", 3_000, 10).create();
        kit.notices.save(kit.productOf(gb), List.of(kit.fullNotice("SMALL_ELECTRONICS", "본체", List.of("본체")), kit.fullNotice("SMALL_ELECTRONICS", "충전기", List.of("충전기"))),
                kit.operator, "ip");
        assertThatThrownBy(() -> publish(gb)).isInstanceOfSatisfying(GroupBuyException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.NOTICE_INCOMPLETE);
            assertThat(e.getExtras()).containsEntry("unassignedSkus", List.of("케이스"));
            assertThat(incompleteNotices(e)).isEmpty();
        });

        // 케이스는 부수 상품 — 공구 전체(기본) 양식이 덮는다(일반원칙 ⑦)
        kit.notices.save(kit.productOf(gb), List.of(kit.fullNotice("SMALL_ELECTRONICS", "본체", List.of()), kit.fullNotice("SMALL_ELECTRONICS", "충전기", List.of("충전기"))),
                kit.operator, "ip");
        publish(gb);
        var saved = kit.notices.notices(kit.productOf(gb));
        assertThat(saved).hasSize(2);
        assertThat(saved.get(1).skuNames()).containsExactly("충전기");
    }

    @Test
    void G43_한_단품은_양식_하나에만_기본_양식은_하나만() {
        long gb = kit.spec(policy()).option("본체", 0, 10).option("충전기", 5_000, 10).create();
        assertThatThrownBy(() -> kit.notices.save(kit.productOf(gb), List.of(kit.fullNotice("SMALL_ELECTRONICS", "A", List.of("충전기")),
                kit.fullNotice("SMALL_ELECTRONICS", "B", List.of("충전기", "본체"))), kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("notices[1].skuNames"));
        assertThatThrownBy(() -> kit.notices.save(kit.productOf(gb), List.of(kit.fullNotice("SMALL_ELECTRONICS", "A", List.of()), kit.fullNotice("SMALL_ELECTRONICS", "B", List.of())),
                kit.operator, "ip")).isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("notices"));
        assertThatThrownBy(() -> kit.notices.save(kit.productOf(gb), List.of(kit.fullNotice("SMALL_ELECTRONICS", "A", List.of("없는 단품"))), kit.operator, "ip"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> kit.notices.save(kit.productOf(gb), List.of(new NoticeDraft("NOT_A_CATEGORY", "A", List.of(), List.of())), kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("notices[0].categoryCode"));
        assertThatThrownBy(() -> kit.notices.save(kit.productOf(gb), List.of(new NoticeDraft("CLOTHING", "A", List.of(), List.of(new NoticeDraft.Value("screen_spec", "x", false, null))))
                , kit.operator, "ip")).as("그 품목에 없는 항목").isInstanceOf(ValidationException.class);
        // DB 도 한 단품은 하나에만
        kit.notices.save(kit.productOf(gb), List.of(kit.fullNotice("SMALL_ELECTRONICS", "A", List.of("충전기")), kit.fullNotice("SMALL_ELECTRONICS", "B", List.of("본체"))),
                kit.operator, "ip");
        var saved = kit.notices.notices(kit.productOf(gb));
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO group_buy_notice_skus (notice_id, product_id, product_sku_id) VALUES (?, ?, ?)", saved.get(1).id(), kit.productOf(gb),
                saved.get(0).skuIds().get(0))).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void G43_모집_뒤_수정은_수정_이력에_사본이_남고_비우는_수정은_거절() {
        long gb = kit.spec(policy()).open();
        int revisionBefore = kit.group(gb).revisionNo();
        NoticeDraft corrected = kit.fullNotice("OTHER_GOODS", "본체", List.of());
        List<NoticeDraft.Value> values = new ArrayList<>(corrected.values().stream().filter(v -> !v.itemKey().equals("country_or_origin")).toList());
        values.add(new NoticeDraft.Value("country_or_origin", "대한민국", false, null));
        kit.notices.save(kit.productOf(gb), List.of(new NoticeDraft("OTHER_GOODS", "본체", List.of(), values)), kit.operator, "ip");

        var g = kit.group(gb);
        assertThat(g.revisionNo()).isEqualTo(revisionBefore + 1);
        assertThat(kit.count("SELECT count(*) FROM group_buy_revisions WHERE group_buy_id = ? AND revision_no = ? AND change_kind = 'NOTICE' "
                + "AND notices -> 0 -> 'values' -> 'country_or_origin' ->> 'value' = '대한민국'", gb, g.revisionNo())).isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM group_buy_revisions WHERE group_buy_id = ? AND revision_no = 1 AND jsonb_array_length(notices) = 1", gb))
                .as("모집 시작 이력에도 고시 사본").isEqualTo(1);

        List<NoticeDraft.Value> emptied = values.stream().filter(v -> !v.itemKey().equals("contact")).toList();
        assertThatThrownBy(() -> kit.notices.save(kit.productOf(gb), List.of(new NoticeDraft("OTHER_GOODS", "본체", List.of(), emptied)), kit.operator, "ip"))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.NOTICE_INCOMPLETE));
        assertThat(kit.notices.notices(kit.productOf(gb)).get(0).values()).containsKey("contact");
        assertThat(kit.group(gb).revisionNo()).isEqualTo(g.revisionNo());
    }

    @Test
    void G43_추가_차수는_같은_상품의_양식을_그대로_쓰고_카탈로그_버전은_만든_때_그대로() {
        long root = kit.spec(policy()).qty(0, 1).option("본체", 0, 1).option("충전기", 0, null).create();
        kit.notices.save(kit.productOf(root), List.of(kit.fullNotice("OTHER_GOODS", "본체", List.of()), kit.fullNotice("SMALL_ELECTRONICS", "충전기", List.of("충전기"))),
                kit.operator, "ip");
        publish(root);
        // 이 상품이 옛 고시 때 만들어진 것처럼(시험 전용 옛 버전 2022-01-01 — 기타 재화 항목 둘뿐)
        kit.jdbc.update("UPDATE group_buy_notices SET catalog_version = '2022-01-01' WHERE product_id = ? AND category_code = 'OTHER_GOODS'", kit.productOf(root));
        kit.paidOrder(kit.newMember(), root, GroupBuyTestKit.lines(kit.skuId(root, "본체"), 1));
        assertThat(kit.group(root).status()).isEqualTo(GroupBuyStatus.CLOSING);

        var round = kit.admin.createRound(root, kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofDays(1)), null, kit.operator, "ip");
        assertThat(kit.productOf(round.groupBuyId())).as("추가 차수 = 같은 상품(결정 89)").isEqualTo(kit.productOf(root));
        var forms = kit.notices.notices(kit.productOf(round.groupBuyId()));
        assertThat(forms).hasSize(2);
        assertThat(forms.get(0).catalogVersion()).isEqualTo("2022-01-01");
        assertThat(forms.get(1).catalogVersion()).isEqualTo("2023-01-01");
        assertThat(forms.get(1).skuNames()).containsExactly("충전기");

        // 옛 버전 양식은 옛 항목으로 검사한다 — 2022 판 항목(둘)만 채워도 완성
        NoticeDraft oldForm = new NoticeDraft("OTHER_GOODS", "본체", List.of(), List.of(new NoticeDraft.Value("product_model_name", "모델 A", false, null),
                new NoticeDraft.Value("contact", "1588-0000", false, null)));
        kit.notices.save(kit.productOf(round.groupBuyId()), List.of(oldForm, kit.fullNotice("SMALL_ELECTRONICS", "충전기", List.of("충전기"))), kit.operator, "ip");
        assertThat(kit.notices.notices(kit.productOf(round.groupBuyId())).get(0).catalogVersion()).as("같은 자리·같은 품목이면 버전 유지").isEqualTo("2022-01-01");
        publish(round.groupBuyId());
        assertThat(kit.group(round.groupBuyId()).status()).isEqualTo(GroupBuyStatus.RECRUITING);

        // 품목을 바꾸면 그 양식은 지금 버전
        long other = kit.spec(policy()).create();
        kit.fillNotice(other);
        kit.jdbc.update("UPDATE group_buy_notices SET catalog_version = '2022-01-01' WHERE product_id = ?", kit.productOf(other));
        kit.notices.save(kit.productOf(other), List.of(kit.fullNotice("CLOTHING", "본체", List.of())), kit.operator, "ip");
        assertThat(kit.notices.notices(kit.productOf(other)).get(0).catalogVersion()).isEqualTo("2023-01-01");
    }

    @Test
    void 상품_옵션에_값을_더해도_같은_조합의_상품_단품_ID_는_그대로라_양식의_적용_단품이_유지된다() {
        GroupBuyTestKit.Spec spec = kit.spec(policy()).option("본체", 0, 10).option("충전기", 5_000, 10);
        long gb = spec.create();
        long product = kit.productOf(gb);
        kit.notices.save(product, List.of(kit.fullNotice("SMALL_ELECTRONICS", "본체", List.of()), kit.fullNotice("SMALL_ELECTRONICS", "충전기", List.of("충전기"))),
                kit.operator, "ip");
        long before = kit.notices.notices(product).get(1).skuIds().getFirst();
        spec.option("케이스", 3_000, 10); // 상품에 값 더하기(공구가 아직 공개 전이라 구성 잠금 없음)
        kit.admin.update(gb, kit.group(gb).version(), spec.draft(), kit.operator, "ip");
        assertThat(kit.products.skus(product)).extracting(s -> s.name()).containsExactly("본체", "충전기", "케이스");
        assertThat(kit.notices.notices(product).get(1).skuIds()).containsExactly(before);
        assertThat(kit.skus(gb)).extracting(s -> s.name()).contains("케이스");
    }

    // ── 판매자 추가 항목(사용자 지시 2026-10-28) ─────────────────────────────

    private NoticeDraft withCustoms(String categoryCode, List<NoticeDraft.Value> customs) {
        NoticeDraft full = kit.fullNotice(categoryCode, "본체", List.of());
        List<NoticeDraft.Value> values = new ArrayList<>(full.values());
        values.addAll(customs);
        return new NoticeDraft(categoryCode, "본체", List.of(), values);
    }

    @Test
    void G43_판매자_추가_항목은_차례대로_저장되고_빈_줄은_빼며_완성_검사_대상이_아니다() {
        long gb = kit.spec(policy()).create();
        kit.notices.save(kit.productOf(gb), List.of(withCustoms("AGRI_LIVESTOCK_FISHERY", List.of(NoticeDraft.Value.custom("수확 시기", "매년 9월 말", 2),
                NoticeDraft.Value.custom("  ", " ", 0), NoticeDraft.Value.custom("원산지 세부", "경기도 이천시", 1)))), kit.operator, "ip");
        var saved = kit.notices.notices(kit.productOf(gb)).get(0);
        assertThat(saved.customItems()).extracting(c -> c.name() + "=" + c.value() + "@" + c.sortOrder())
                .containsExactly("원산지 세부=경기도 이천시@0", "수확 시기=매년 9월 말@1");
        assertThat(saved.values()).doesNotContainKeys("수확 시기", "원산지 세부");
        publish(gb); // 추가 항목은 선택 — 고시 항목만 차면 된다

        // 모집 뒤 수정 이력에 추가 항목도 사본으로
        kit.notices.save(kit.productOf(gb), List.of(withCustoms("AGRI_LIVESTOCK_FISHERY", List.of(NoticeDraft.Value.custom("수확 시기", "매년 10월 초", null)))), kit.operator, "ip");
        assertThat(kit.count("SELECT count(*) FROM group_buy_revisions WHERE group_buy_id = ? AND change_kind = 'NOTICE' "
                + "AND notices -> 0 -> 'customItems' -> 0 ->> 'value' = '매년 10월 초' AND jsonb_array_length(notices -> 0 -> 'customItems') = 1", gb)).isEqualTo(1);
    }

    @Test
    void G43_추가_항목_이름이_고시_항목과_같으면_거절_20개_초과_거절_이름_겹침_반쪽_줄_거절() {
        long gb = kit.spec(policy()).create();
        // 고시 항목 이름 그대로(공백 무시) · 쉼표로 나뉜 이름(「크기, 무게」의 크기 · 「제조자, 수입품의 경우 …」의 제조자)
        for (String reserved : List.of("제조국", "품명및모델명", "크기", "제조자")) {
            String category = reserved.equals("품명및모델명") || reserved.equals("제조자") ? "OTHER_GOODS" : "SMALL_ELECTRONICS";
            assertThatThrownBy(() -> kit.notices.save(kit.productOf(gb), List.of(withCustoms(category, List.of(NoticeDraft.Value.custom(reserved, "값", null)))), kit.operator, "ip"))
                    .as(reserved).isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("notices[0].customItems[0].name"));
        }
        List<NoticeDraft.Value> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            many.add(NoticeDraft.Value.custom("추가 " + i, "내용", i));
        }
        assertThatThrownBy(() -> kit.notices.save(kit.productOf(gb), List.of(withCustoms("OTHER_GOODS", many)), kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("notices[0].customItems"));
        kit.notices.save(kit.productOf(gb), List.of(withCustoms("OTHER_GOODS", many.subList(0, 20))), kit.operator, "ip"); // 20개까지는 된다
        assertThatThrownBy(() -> kit.notices.save(kit.productOf(gb), List.of(withCustoms("OTHER_GOODS", List.of(NoticeDraft.Value.custom("보관 방법", "서늘한 곳", 0),
                NoticeDraft.Value.custom("보관방법", "냉장", 1)))), kit.operator, "ip")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> kit.notices.save(kit.productOf(gb), List.of(withCustoms("OTHER_GOODS", List.of(NoticeDraft.Value.custom("사이즈 표", " ", 0)))), kit.operator, "ip"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> kit.notices.save(kit.productOf(gb), List.of(withCustoms("OTHER_GOODS", List.of(NoticeDraft.Value.custom("x".repeat(41), "내용", 0)))), kit.operator,
                "ip")).isInstanceOf(ValidationException.class);
        // DB: 추가 항목 줄은 키가 없고 이름·내용이 있어야 한다
        long noticeId = kit.notices.notices(kit.productOf(gb)).get(0).id();
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO group_buy_notice_values (id, notice_id, custom, item_key, custom_name, value, sort_order) "
                + "VALUES (?, ?, true, 'contact', '연락처', '1588', 99)", kit.ids.nextId(), noticeId)).hasMessageContaining("ck_gbnv_kind");
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO group_buy_notice_values (id, notice_id, custom, item_key, value) VALUES (?, ?, false, NULL, 'x')",
                kit.ids.nextId(), noticeId)).hasMessageContaining("ck_gbnv_kind");
    }

    @Test
    void G43_추가_차수는_같은_상품이라_판매자_추가_항목도_그대로_보인다() {
        long root = kit.spec(policy()).qty(0, 1).create();
        kit.notices.save(kit.productOf(root), List.of(withCustoms("OTHER_GOODS", List.of(NoticeDraft.Value.custom("호환 기종", "ROBO-1, ROBO-2", 0)))), kit.operator, "ip");
        publish(root);
        kit.paidOrder(kit.newMember(), root, 1);
        var round = kit.admin.createRound(root, kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofDays(1)), null, kit.operator, "ip");
        assertThat(kit.notices.notices(kit.productOf(round.groupBuyId())).get(0).customItems()).extracting(c -> c.name() + "=" + c.value())
                .containsExactly("호환 기종=ROBO-1, ROBO-2");
    }

    @Test
    void 상세페이지_참조만_적은_항목은_막지_않고_경고한다() {
        long gb = kit.spec(policy()).create();
        NoticeDraft full = kit.fullNotice("OTHER_GOODS", "본체", List.of());
        List<NoticeDraft.Value> values = new ArrayList<>(full.values().stream().filter(v -> !v.itemKey().equals("legal_certification")).toList());
        values.add(new NoticeDraft.Value("legal_certification", "상세페이지 참조", false, null));
        List<GroupBuyNoticeService.DetailPageWarning> warnings = kit.notices.save(kit.productOf(gb), List.of(new NoticeDraft("OTHER_GOODS", "본체", List.of(), values)), kit.operator, "ip");
        assertThat(warnings).containsExactly(new GroupBuyNoticeService.DetailPageWarning(0, "legal_certification"));
        publish(gb);
    }
}
