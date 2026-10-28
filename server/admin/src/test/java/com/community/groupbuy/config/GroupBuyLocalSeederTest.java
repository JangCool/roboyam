package com.community.groupbuy.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.config.BatchProperties;
import com.community.commerce.CommerceAdminTestBase;
import com.community.commerce.sales.CommerceSalesService;
import com.community.commerce.sales.CommerceSalesStore;
import com.community.groupbuy.service.GroupBuyAdminService;
import com.community.groupbuy.service.GroupBuyNoticeCatalog;
import com.community.groupbuy.service.GroupBuyNoticeService;
import com.community.groupbuy.service.GroupBuyProductService;
import com.community.groupbuy.service.ShippingPolicyService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * local 공구 시드(계약 결정 11·87, commerce.md 20절) — 실제 서비스로 넣는다: 켜는 조건 검사를 통과해 판매 켜짐 · 예시 품목 여덟(각 품목의 고시 양식이 다 참) · 공구 여덟
 * (성사형 모집 중 · 선착순 즉시 발송(옵션 넷) · 마감 임박 · 조기 마감으로 끝난 1차 · 공개된 예정). 실제 관리자 앱 문맥 + PostgreSQL.
 */
class GroupBuyLocalSeederTest extends CommerceAdminTestBase {

    @Autowired
    private CommerceSalesStore salesStore;
    @Autowired
    private CommerceSalesService sales;
    @Autowired
    private ShippingPolicyService policies;
    @Autowired
    private GroupBuyProductService products;
    @Autowired
    private GroupBuyNoticeService notices;
    @Autowired
    private GroupBuyNoticeCatalog catalog;
    @Autowired
    private GroupBuyAdminService admin;
    @Autowired
    private ObjectProvider<Clock> clocks;

    @AfterEach
    void restore() {
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");
        jdbc.update("UPDATE commerce_sales_checklist SET checked = false, checked_by = NULL, checked_at = NULL");
        jdbc.update("UPDATE commerce_business_info SET company_name = '' WHERE id = 1");
    }

    @Test
    void 판매_조건을_실제로_채워_켜고_예시_품목_여덟과_공구_여덟을_넣는다() {
        jdbc.update("UPDATE commerce_business_info SET company_name = '' WHERE id = 1");
        GroupBuyLocalSeeder seeder = new GroupBuyLocalSeeder(new BatchProperties(true, "Asia/Seoul", 30, 60, 4, 2000, 120, 30), dataSource, salesStore, sales, policies,
                products, notices, catalog, admin, clocks);
        seeder.seedSales();
        assertThat(salesStore.businessInfo().ready()).isTrue();
        assertThat(salesStore.groupBuyOpen()).as("켜는 조건 검사를 통과해 켜짐(가짜 PG·가짜 본인인증·택배사 시드)").isTrue();

        int before = jdbc.queryForObject("SELECT count(*) FROM group_buy_products", Integer.class);
        try {
            seeder.seedCatalog();
        } catch (com.community.zconfiguration.exception.ValidationException e) {
            throw new AssertionError("시드 검증 실패 " + e.getFields(), e);
        }
        List<Map<String, Object>> seeded = jdbc.queryForList("SELECT p.id AS product_id, p.name, g.status, g.close_reason, g.fulfillment, g.published_at IS NOT NULL AS published "
                + "FROM group_buy_products p JOIN group_buys g ON g.product_id = p.id WHERE p.name IN ('2026 햅쌀 신동진', '제철 과일 박스', '제주 손질 고등어 10팩', '전라도 포기김치', "
                + "'수제 바비큐 소스 2병', '로보얌 오버핏 티셔츠', '경량 러닝화', '휴머노이드 로봇 키트')");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM group_buy_products", Integer.class) - before).isEqualTo(8);
        assertThat(seeded).hasSize(8).allSatisfy(r -> assertThat(r.get("published")).isEqualTo(true));
        assertThat(seeded).filteredOn(r -> "RECRUITING".equals(r.get("status"))).hasSize(6);
        assertThat(seeded).filteredOn(r -> "EARLY_CLOSED".equals(r.get("close_reason"))).hasSize(1);
        assertThat(seeded).filteredOn(r -> "DRAFT".equals(r.get("status"))).as("공개된 예정").hasSize(1);
        assertThat(seeded).filteredOn(r -> "AFTER_CLOSE".equals(r.get("fulfillment"))).as("성사형").hasSize(3);
        for (Map<String, Object> r : seeded) {
            var completeness = notices.completeness(((Number) r.get("product_id")).longValue());
            assertThat(completeness.noNotice()).as(r.get("name") + " 고시 양식").isFalse();
            assertThat(completeness.notices()).as(r.get("name") + " 빈 항목").isEmpty();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM group_buy_skus s JOIN group_buys g ON g.id = s.group_buy_id JOIN group_buy_products p ON p.id = g.product_id "
                + "WHERE p.name = '제철 과일 박스'", Integer.class)).as("사과/감귤 × 3kg/5kg").isEqualTo(4);
    }
}
