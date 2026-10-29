package com.community.home.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceApiTestBase;
import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.dto.NoticeDraft;
import com.community.groupbuy.dto.ProductDraft;
import com.community.groupbuy.dto.response.GroupBuyMemberView;
import com.community.groupbuy.service.GroupBuyAdminService;
import com.community.groupbuy.service.GroupBuyNoticeCatalog;
import com.community.groupbuy.service.GroupBuyNoticeService;
import com.community.groupbuy.service.GroupBuyProductService;
import com.community.groupbuy.service.ShippingPolicyService;
import com.community.home.dto.response.gadget.GroupBuyDataView;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * 홈 {@code GROUP_BUY} 가젯 데이터 — 회원 공구 목록 진행 탭과 같은 거름(공개 · 숨긴 카테고리 제외)에서 모집 중만, 마감 이른 순, 가젯 설정 {@code count}(1~10),
 * 판매 스위치는 거르지 않고 {@code salesOpen} 으로. DB 를 다른 시험과 같이 쓰므로 공구 마감을 몇 분 뒤로 잡아 앞줄에 오게 한다.
 */
class HomeGadgetGroupBuyTest extends CommerceApiTestBase {

    @Autowired
    private HomeGadgetService service;
    @Autowired
    private GroupBuyProductService products;
    @Autowired
    private GroupBuyAdminService admin;
    @Autowired
    private ShippingPolicyService policies;
    @Autowired
    private GroupBuyNoticeService notices;
    @Autowired
    private GroupBuyNoticeCatalog catalog;

    private final List<Long> categories = new ArrayList<>();
    private long op;
    private String policy;

    @BeforeEach
    void setUp() {
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = true WHERE id = 1");
        op = newOperator();
        policy = "G" + Long.toString(ids.nextId(), 36).toUpperCase();
        policies.schedule(new ShippingPolicyService.PolicyInput(policy, "시험 배송", 3_000, null, 3_000, "ONE_WAY", null), op, "ip");
    }

    /** 이 시험의 공구가 다음 시험의 앞줄을 차지하지 않게 카테고리를 숨긴다 */
    @AfterEach
    void restore() {
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");
        categories.forEach(id -> jdbc.update("UPDATE group_buy_categories SET hidden = true WHERE id = ?", id));
    }

    /** 공구 하나(선착순·즉시 발송, 정상가 30,000 · 공구가 25,000) — 마감은 지금부터 {@code minutes} 분 뒤, {@code publish} 면 공개(시작이 지나 바로 모집 중) */
    private long groupBuy(long category, int minutes, boolean publish) {
        long product = products.create(new ProductDraft(category, "가젯 시험 상품", null, null, "<p>설명</p>", 30_000L, "OFFICIAL_STORE", "시험", null, List.of(),
                List.of(new ProductDraft.SkuDraft(null, "기본", List.of(), 10_000L, true)), List.<GroupBuyDraft.ImageRef>of()), op, "ip");
        List<NoticeDraft.Value> values = catalog.current().category("OTHER_GOODS").orElseThrow().items().stream()
                .map(i -> new NoticeDraft.Value(i.key(), "시험 값", false, null)).toList();
        notices.save(product, List.of(new NoticeDraft("OTHER_GOODS", "본체", List.of(), values)), op, "ip");
        Instant now = clock.instant();
        long gb = admin.create(new GroupBuyDraft(product, "가젯 시험 공구 " + minutes, 25_000, policy, 0, 100, 5, now.minus(Duration.ofMinutes(1)),
                now.plus(Duration.ofMinutes(minutes)), "DEMAND", "IMMEDIATE", null, 3, null, false, List.of(), List.of(), true), op, "ip");
        if (publish) {
            int version = jdbc.queryForObject("SELECT version FROM group_buys WHERE id = ?", Integer.class, gb);
            admin.publish(gb, version, true, op, "ip");
        }
        return gb;
    }

    private long category() {
        long id = products.createCategory("가젯 " + ids.nextId(), null, 0, op, "ip");
        categories.add(id);
        return id;
    }

    private static JsonNode count(int n) {
        return HomeLayoutJson.object().put("count", n);
    }

    private static List<String> ids(GroupBuyDataView d) {
        return d.items().stream().map(GroupBuyMemberView.ListItem::id).toList();
    }

    @Test
    void 모집_중인_공개_공구만_마감_이른_순으로_목록과_같은_줄_모양() {
        long category = category();
        long later = groupBuy(category, 7, true);
        long first = groupBuy(category, 5, true);
        long second = groupBuy(category, 6, true);

        GroupBuyDataView d = service.groupBuy(count(3));

        assertThat(ids(d)).containsExactly(Long.toString(first), Long.toString(second), Long.toString(later));
        GroupBuyMemberView.ListItem item = d.items().getFirst();
        assertThat(item.status()).isEqualTo("RECRUITING");
        assertThat(item.groupPrice()).isEqualTo(25_000);
        assertThat(item.normalPrice()).isEqualTo(30_000);
        assertThat(item.remainingQty()).isEqualTo(100);
        assertThat(item.closingSoon()).isTrue();
        assertThat(item.reviews().count()).isZero();
        assertThat(d.salesOpen()).isTrue();
    }

    @Test
    void 공개_전_집계_중_숨긴_카테고리는_빠진다() {
        long category = category();
        long shown = groupBuy(category, 5, true);
        long unpublished = groupBuy(category, 5, false);
        long closing = groupBuy(category, 5, true);
        jdbc.update("UPDATE group_buys SET status = 'CLOSING', closing_at = now() WHERE id = ?", closing);
        long hiddenCategory = category();
        long hidden = groupBuy(hiddenCategory, 5, true);
        jdbc.update("UPDATE group_buy_categories SET hidden = true WHERE id = ?", hiddenCategory);

        List<String> found = ids(service.groupBuy(count(10)));

        assertThat(found).contains(Long.toString(shown))
                .doesNotContain(Long.toString(unpublished), Long.toString(closing), Long.toString(hidden));
    }

    @Test
    void 개수는_설정_count_를_1에서_10_사이로_따르고_없으면_5() {
        long category = category();
        for (int i = 0; i < 11; i++) {
            groupBuy(category, 5 + i, true);
        }

        assertThat(service.groupBuy(count(2)).items()).hasSize(2);
        assertThat(service.groupBuy(count(0)).items()).hasSize(1);
        assertThat(service.groupBuy(count(99)).items()).hasSize(HomeGadgetService.GROUP_BUY_MAX);
        assertThat(service.groupBuy(null).items()).hasSize(HomeGadgetService.GROUP_BUY_DEFAULT);
    }

    @Test
    void 판매_스위치가_꺼져도_목록처럼_공구는_보이고_salesOpen_false() {
        long gb = groupBuy(category(), 5, true);
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");

        GroupBuyDataView d = service.groupBuy(count(10));

        assertThat(ids(d)).contains(Long.toString(gb));
        assertThat(d.salesOpen()).isFalse();
    }
}
