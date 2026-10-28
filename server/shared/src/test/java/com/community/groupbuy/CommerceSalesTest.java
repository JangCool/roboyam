package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.commerce.sales.BusinessInfo;
import com.community.commerce.sales.CommerceSalesException;
import com.community.commerce.sales.CommerceSalesService;
import com.community.commerce.sales.CommerceSalesStore;
import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.dto.ProductDraft;
import com.community.groupbuy.service.DefaultGroupBuySalesSwitch;
import com.community.groupbuy.service.GroupBuyDescriptionHtml;
import com.community.groupbuy.service.GroupBuySettingsService;
import com.community.identity.provider.FakeIdentityVerifier;
import com.community.identity.provider.IdentityVerifier;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.payment.gateway.PaymentGateway;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * 판매 스위치·운영 체크리스트·사업자 정보(commerce.md 20절, 계약 결정 18·19·56 — G41)·공구 설정(결정 10)·상품 설명 정화(결정 2). 실제 PostgreSQL.
 * 판매 스위치·사업자 정보는 1행 표라 시험마다 처음 상태로 되돌린다.
 */
class CommerceSalesTest {

    private static GroupBuyTestKit kit;
    private static CommerceSalesStore store;
    private static CommerceSalesService sales;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
        store = new CommerceSalesStore(kit.dataSource);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("gateway", kit.spring.getBean(PaymentGateway.class));
        beans.registerSingleton("identity", new FakeIdentityVerifier(Clock.systemUTC()));
        beans.registerSingleton("clock", kit.clock);
        sales = new CommerceSalesService(store, kit.spring.getBean(com.community.audit.service.AuditLogService.class), kit.spring.getBean(NotificationPublisher.class),
                beans.getBeanProvider(PaymentGateway.class), beans.getBeanProvider(IdentityVerifier.class), beans.getBeanProvider(Clock.class));
    }

    @BeforeEach
    void reset() {
        kit.reset();
        clear();
    }

    @AfterEach
    void restore() {
        clear();
    }

    private static void clear() {
        kit.jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false, changed_at = NULL, changed_by = NULL, change_reason = NULL WHERE id = 1");
        kit.jdbc.update("UPDATE commerce_sales_checklist SET checked = false, checked_by = NULL, checked_at = NULL");
        kit.jdbc.update("UPDATE commerce_business_info SET company_name = '', representative = '', business_no = '', mail_order_no = '', mail_order_authority = '', "
                + "address = '', phone = '', email = '', business_hours = '', privacy_officer = '', hosting_provider = '', shipping_refund_policy = '' WHERE id = 1");
    }

    private static BusinessInfo filled() {
        return new BusinessInfo("시험상회", "홍길동", "123-45-67891", "2026-서울-0001", "서울특별시 강남구", "서울특별시 강남구 1", "02-000-0000", "help@test.local",
                "평일 10~17시", "홍길동", "시험 호스팅", "7일 안 청약철회", null, null);
    }

    private void checkAll() {
        store.checklist().forEach(c -> sales.check(c.key(), true, kit.operator, "ip"));
    }

    @Test
    void 설치_직후에는_꺼짐_켜기는_최고_관리자만_자동_조건과_체크리스트가_다_차야_한다() {
        assertThat(sales.state().enabled()).isFalse();
        assertThat(sales.groupBuyOpen()).isFalse();

        assertThatThrownBy(() -> sales.toggle(true, "판매 시작합니다", kit.operator, false, "ip"))
                .isInstanceOfSatisfying(CommerceSalesException.class, e -> assertThat(e.getErrorCode()).isEqualTo(CommerceSalesException.ROOT_ONLY));
        assertThatThrownBy(() -> sales.toggle(true, "판매 시작합니다", kit.operator, true, "ip"))
                .isInstanceOfSatisfying(CommerceSalesException.class, e -> assertThat(e.getErrorCode()).isEqualTo(CommerceSalesException.NOT_READY));
        assertThat(sales.state().missing()).contains("BUSINESS_INFO", "BUSINESS_REG", "RETURN_ADDRESS");

        sales.saveBusinessInfo(filled(), kit.operator, "ip");
        assertThat(sales.state().missing()).doesNotContain("BUSINESS_INFO", "PG_KEYS", "CARRIER", "IDENTITY");
        assertThatThrownBy(() -> sales.toggle(true, "판매 시작합니다", kit.operator, true, "ip")).isInstanceOf(CommerceSalesException.class);
        checkAll();
        assertThat(sales.state().canEnable()).isTrue();
        assertThat(sales.state().checklist()).allSatisfy(c -> assertThat(c.checkedBy()).isNotNull());

        kit.audits.clear();
        CommerceSalesService.SalesState on = sales.toggle(true, "판매 시작합니다", kit.operator, true, "ip");
        assertThat(on.enabled()).isTrue();
        assertThat(kit.audits).anySatisfy(a -> {
            assertThat(a.action()).isEqualTo(AuditActions.COMMERCE_SALES_TOGGLE);
            assertThat(a.detail()).containsEntry("before", false).containsEntry("after", true).containsEntry("reason", "판매 시작합니다");
        });
        assertThat(kit.operatorNotices).contains(AdminNotificationEvent.COMMERCE_SALES_TOGGLE);

        // 끄기는 최고 관리자가 아니어도(설정 권한 — 컨트롤러가 본다), 조건과 상관없이 바로
        store.check("TERMS", false, kit.operator, java.time.Instant.now());
        CommerceSalesService.SalesState off = sales.toggle(false, "문제가 생겨 멈춥니다", kit.operator, false, "ip");
        assertThat(off.enabled()).isFalse();
        assertThatThrownBy(() -> sales.toggle(false, "짧음", kit.operator, false, "ip")).as("사유 5~200자").isInstanceOf(ValidationException.class);
    }

    @Test
    void 판매_스위치_구현은_그_트랜잭션_안의_DB_값을_읽는다_캐시_없음_결정19() {
        DefaultGroupBuySalesSwitch sw = new DefaultGroupBuySalesSwitch(store);
        assertThat(sw.groupBuyOpen()).isFalse();
        kit.jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = true WHERE id = 1");
        assertThat(sw.groupBuyOpen()).isTrue();
        kit.jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");
        assertThat(sw.groupBuyOpen()).isFalse();
    }

    @Test
    void G41_공개_정보는_판매가_켜져_있고_필수_칸이_찼을_때만_사업자_정보를_싣는다() {
        assertThat(sales.publicInfo().business()).as("꺼짐 + 빈 칸").isNull();
        sales.saveBusinessInfo(filled(), kit.operator, "ip");
        CommerceSalesService.PublicInfo closed = sales.publicInfo();
        assertThat(closed.salesOpen()).isFalse();
        assertThat(closed.ready()).isTrue();
        assertThat(closed.business()).as("꺼짐 — 숨김").isNull();
        kit.jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = true WHERE id = 1");
        CommerceSalesService.PublicInfo open = sales.publicInfo();
        assertThat(open.business()).isNotNull();
        assertThat(open.business().companyName()).isEqualTo("시험상회");
        assertThat(open.business().mailOrderAuthority()).isEqualTo("서울특별시 강남구");
        kit.jdbc.update("UPDATE commerce_business_info SET phone = '' WHERE id = 1");
        assertThat(sales.publicInfo().business()).as("필수 칸이 비면 숨김").isNull();
    }

    @Test
    void 사업자등록번호는_모양과_체크섬_칸_길이와_전자우편_모양을_본다_바뀐_칸만_감사() {
        assertThat(CommerceSalesService.validBusinessNo("123-45-67891")).isTrue();
        assertThat(CommerceSalesService.validBusinessNo("123-45-67890")).isFalse();
        assertThat(CommerceSalesService.validBusinessNo("1234567891")).isFalse();
        BusinessInfo wrong = new BusinessInfo("시험상회", "홍길동", "123-45-67890", "", "", "", "", "", "", "", "", "", null, null);
        assertThatThrownBy(() -> sales.saveBusinessInfo(wrong, kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("values.businessNo"));
        BusinessInfo email = new BusinessInfo("시험상회", "", "", "", "", "", "", "not-an-email", "", "", "", "", null, null);
        assertThatThrownBy(() -> sales.saveBusinessInfo(email, kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("values.email"));
        BusinessInfo longPolicy = new BusinessInfo("시험상회", "", "", "", "", "", "", "", "", "", "", "가".repeat(3001), null, null);
        assertThatThrownBy(() -> sales.saveBusinessInfo(longPolicy, kit.operator, "ip")).isInstanceOf(ValidationException.class);

        kit.audits.clear();
        sales.saveBusinessInfo(filled(), kit.operator, "ip");
        assertThat(kit.audits).singleElement().satisfies(a -> {
            assertThat(a.action()).isEqualTo(AuditActions.COMMERCE_BUSINESS_INFO_UPDATE);
            assertThat(a.detail().get("fields").toString()).contains("companyName").contains("businessNo").doesNotContain("홍길동");
        });
        kit.audits.clear();
        sales.saveBusinessInfo(filled(), kit.operator, "ip");
        assertThat(kit.audits).as("바뀐 칸이 없으면 감사 없음").isEmpty();
    }

    @Test
    void 공구_설정은_범위_밖이면_400_바뀐_칸만_전후_감사_휴일은_겹침을_지우고_차례대로() {
        GroupBuySettingsService settings = kit.settingsService;
        GroupBuySettingsService.Values before = settings.load();
        Map<String, Long> numbers = new LinkedHashMap<>(before.numbers());
        try {
            Map<String, Long> bad = new LinkedHashMap<>(numbers);
            bad.put("holdMinutes", 2L);
            assertThatThrownBy(() -> settings.save(bad, List.of(), kit.operator, "ip"))
                    .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("values.holdMinutes"));
            Map<String, Long> missing = new LinkedHashMap<>(numbers);
            missing.remove("largeRefundThreshold");
            assertThatThrownBy(() -> settings.save(missing, List.of(), kit.operator, "ip")).isInstanceOf(ValidationException.class);

            kit.audits.clear();
            Map<String, Long> changed = new LinkedHashMap<>(numbers);
            changed.put("largeRefundThreshold", 0L);
            changed.put("holdMinutes", 7L);
            LocalDate d1 = LocalDate.of(2026, 12, 25);
            LocalDate d2 = LocalDate.of(2026, 10, 9);
            GroupBuySettingsService.Values after = settings.save(changed, List.of(d1, d2, d1), kit.operator, "ip");
            assertThat(after.numbers()).containsEntry("largeRefundThreshold", 0L).containsEntry("holdMinutes", 7L);
            assertThat(after.holidays()).containsExactly(d2, d1);
            assertThat(kit.groupStore.settings().holdMinutes()).as("업무가 읽는 값도 바로 바뀐다(결정 19)").isEqualTo(7);
            assertThat(kit.audits).singleElement().satisfies(a -> {
                assertThat(a.action()).isEqualTo(AuditActions.GROUPBUY_SETTINGS_UPDATE);
                assertThat(((Map<?, ?>) a.detail().get("after")).keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder("largeRefundThreshold", "holdMinutes", "holidays");
            });
        } finally {
            settings.save(numbers, before.holidays(), kit.operator, "ip");
        }
    }

    @Test
    void 상품_설명은_서버가_정화한다_스크립트_이벤트_속성_iframe_은_사라지고_이미지_링크는_남는다_결정2() {
        String raw = "<p onclick=\"x()\">설명<script>alert(1)</script></p><img src=\"https://cdn.example.com/a.jpg\" alt=\"사진\" onerror=\"x()\">"
                + "<img src=\"javascript:alert(1)\"><iframe src=\"https://evil.example\"></iframe><a href=\"https://shop.example\">링크</a>"
                + "<p style=\"color:red\">빨강</p>";
        String safe = GroupBuyDescriptionHtml.sanitize(raw);
        assertThat(safe).doesNotContain("script").doesNotContain("onclick").doesNotContain("onerror").doesNotContain("iframe").doesNotContain("javascript")
                .doesNotContain("style").contains("<img src=\"https://cdn.example.com/a.jpg\" alt=\"사진\" />")
                .contains("target=\"_blank\"").contains("rel=\"nofollow noopener noreferrer\"").contains("설명").contains("빨강");

        long product = kit.products.create(new ProductDraft(kit.category, "정화 시험 상품", null, null, raw, 10_000L, "OFFICIAL_STORE", "시험", null, List.of(),
                List.of(new ProductDraft.SkuDraft(null, "기본", List.of(), 5_000L, true)), List.<GroupBuyDraft.ImageRef>of()), kit.operator, "ip");
        assertThat(kit.products.product(product).description()).isEqualTo(safe);
    }
}
