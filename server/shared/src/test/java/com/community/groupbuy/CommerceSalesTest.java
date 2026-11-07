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
import com.community.site.entity.SiteOperatorStage;
import com.community.testsupport.JpaTestContext;
import com.community.testsupport.SiteOperatorFixture;
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
        store = new CommerceSalesStore(JpaTestContext.query(), kit.dataSource);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("gateway", kit.spring.getBean(PaymentGateway.class));
        beans.registerSingleton("identity", new FakeIdentityVerifier(Clock.systemUTC()));
        beans.registerSingleton("clock", kit.clock);
        // 운영과 같은 트랜잭션 경계(@Transactional) — 저장소의 QueryDSL 쓰기·잠금은 트랜잭션이 있어야 한다
        sales = JpaTestContext.transactional(new CommerceSalesService(store, kit.spring.getBean(com.community.audit.service.AuditLogService.class),
                kit.spring.getBean(NotificationPublisher.class), beans.getBeanProvider(PaymentGateway.class), beans.getBeanProvider(IdentityVerifier.class),
                beans.getBeanProvider(Clock.class)));
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
        kit.jdbc.update("UPDATE commerce_business_info SET business_hours = '', shipping_refund_policy = '' WHERE id = 1");
        SiteOperatorFixture.clear(kit.jdbc);
    }

    /** 사업자 정보의 정본 = 운영 주체(terms-operator-contract) — 개인사업자 + 시험 사업자 정보 */
    private static void fillOperator() {
        SiteOperatorFixture.fillBusiness(kit.jdbc);
    }

    /** 커머스 사업자 정보 저장 요청 — 겹치는 칸에 운영 주체와 다른 값을 넣는다(저장이 무시해야 한다) */
    private static BusinessInfo filled() {
        return new BusinessInfo("다른상회", "김다른", "123-45-67890", "2099-다른-9999", "다른 기관", "다른 주소", "010-9999-9999", "other@test.local",
                "평일 10~17시", "김다른", "다른 호스팅", "7일 안 청약철회", null, null);
    }

    /** 운영 주체 저장 트랜잭션처럼(판매 스위치 잠금은 트랜잭션 안에서만 — MANDATORY) */
    private static void inTx(Runnable work) {
        new org.springframework.transaction.support.TransactionTemplate(JpaTestContext.transactions()).executeWithoutResult(status -> work.run());
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
        assertThat(sales.state().missing()).contains("BUSINESS_INFO", "OPERATOR_STAGE", "BUSINESS_REG", "RETURN_ADDRESS");

        fillOperator();
        assertThat(sales.state().missing()).doesNotContain("BUSINESS_INFO", "OPERATOR_STAGE", "PG_KEYS", "CARRIER", "IDENTITY");
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
        inTx(() -> store.check("TERMS", false, kit.operator, java.time.Instant.now()));
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
        fillOperator();
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
        kit.jdbc.update("UPDATE site_operator SET phone = NULL WHERE id = 1");
        assertThat(sales.publicInfo().business()).as("필수 칸(운영 주체 전화)이 비면 숨김").isNull();
    }

    @Test
    void 정본_공개_사업자_정보의_겹치는_칸은_운영_주체_값에서_나온다_법인은_회사명_보호책임자는_운영_주체의_보호책임자() {
        fillOperator();
        sales.saveBusinessInfo(filled(), kit.operator, "ip");
        kit.jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = true WHERE id = 1");
        BusinessInfo sole = sales.publicInfo().business();
        assertThat(sole.companyName()).isEqualTo("시험상회");
        assertThat(sole.representative()).isEqualTo("홍길동");
        assertThat(sole.businessNo()).isEqualTo("123-45-67891");
        assertThat(sole.mailOrderNo()).isEqualTo("2026-서울-0001");
        assertThat(sole.address()).isEqualTo("서울특별시 강남구 1");
        assertThat(sole.phone()).isEqualTo("02-000-0000");
        assertThat(sole.email()).isEqualTo("help@test.local");
        assertThat(sole.hostingProvider()).isEqualTo("시험 호스팅");
        assertThat(sole.privacyOfficer()).as("「운영자와 같음」이면 운영자 이름").isEqualTo("홍길동");
        assertThat(sole.businessHours()).as("커머스 고유 칸은 커머스 값").isEqualTo("평일 10~17시");
        assertThat(sole.shippingRefundPolicy()).isEqualTo("7일 안 청약철회");

        kit.jdbc.update("UPDATE site_operator SET stage = 'CORPORATION', business_name = '로보얌 주식회사', corporate_number = '110111-0000000', "
                + "privacy_officer_same = false, privacy_officer_name = '김보호', privacy_officer_email = 'privacy@test.local' WHERE id = 1");
        BusinessInfo corp = sales.publicInfo().business();
        assertThat(corp.companyName()).as("법인은 상호 자리에 회사명").isEqualTo("로보얌 주식회사");
        assertThat(corp.privacyOfficer()).as("따로 적은 보호책임자").isEqualTo("김보호");
        assertThat(sales.businessInfo().companyName()).as("관리자 읽기 응답도 운영 주체 값").isEqualTo("로보얌 주식회사");
    }

    @Test
    void 정본_커머스_사업자_정보_저장은_겹치는_칸을_받아도_무시하고_고유_칸만_검사_저장_바뀐_칸만_감사() {
        assertThat(CommerceSalesService.validBusinessNo("123-45-67891")).isTrue();
        assertThat(CommerceSalesService.validBusinessNo("123-45-67890")).isFalse();
        assertThat(CommerceSalesService.validBusinessNo("1234567891")).isFalse();
        fillOperator();
        // 겹치는 칸의 틀린 값(체크섬이 틀린 등록번호·이메일 모양 아님)은 검사하지 않고 버린다
        BusinessInfo wrong = new BusinessInfo("시험상회", "홍길동", "123-45-67890", "", "", "", "", "not-an-email", "", "", "", "", null, null);
        BusinessInfo saved = sales.saveBusinessInfo(wrong, kit.operator, "ip");
        assertThat(saved.businessNo()).isEqualTo("123-45-67891");
        assertThat(saved.email()).isEqualTo("help@test.local");
        BusinessInfo longPolicy = new BusinessInfo("", "", "", "", "", "", "", "", "", "", "", "가".repeat(3001), null, null);
        assertThatThrownBy(() -> sales.saveBusinessInfo(longPolicy, kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("values.shippingRefundPolicy"));
        BusinessInfo longHours = new BusinessInfo("", "", "", "", "", "", "", "", "가".repeat(101), "", "", "", null, null);
        assertThatThrownBy(() -> sales.saveBusinessInfo(longHours, kit.operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("values.businessHours"));

        kit.audits.clear();
        BusinessInfo after = sales.saveBusinessInfo(filled(), kit.operator, "ip");
        assertThat(after.companyName()).as("다른 상호를 보내도 운영 주체 값 그대로").isEqualTo("시험상회");
        assertThat(after.representative()).isEqualTo("홍길동");
        assertThat(after.mailOrderNo()).isEqualTo("2026-서울-0001");
        assertThat(after.phone()).isEqualTo("02-000-0000");
        assertThat(after.privacyOfficer()).isEqualTo("홍길동");
        assertThat(after.businessHours()).isEqualTo("평일 10~17시");
        assertThat(kit.jdbc.queryForObject("SELECT business_name FROM site_operator WHERE id = 1", String.class)).as("운영 주체 행도 그대로").isEqualTo("시험상회");
        assertThat(kit.audits).singleElement().satisfies(a -> {
            assertThat(a.action()).isEqualTo(AuditActions.COMMERCE_BUSINESS_INFO_UPDATE);
            assertThat(a.detail().get("fields").toString()).contains("businessHours").contains("shippingRefundPolicy").doesNotContain("companyName")
                    .doesNotContain("홍길동");
        });
        kit.audits.clear();
        sales.saveBusinessInfo(filled(), kit.operator, "ip");
        assertThat(kit.audits).as("바뀐 칸이 없으면 감사 없음").isEmpty();
    }

    @Test
    void 정본_판매_켜기는_운영_주체가_개인사업자_이상이고_통신판매업_신고번호가_있어야_한다() {
        checkAll();
        // 개인 단계(연락 칸만 있음) — 켜기 조건 OPERATOR_STAGE 가 빠진다
        kit.jdbc.update("UPDATE site_operator SET operator_name = '홍길동', contact_email = 'help@test.local', phone = '02-000-0000' WHERE id = 1");
        assertThat(sales.state().missing()).contains("OPERATOR_STAGE");
        assertThatThrownBy(() -> sales.toggle(true, "판매 시작합니다", kit.operator, true, "ip"))
                .isInstanceOfSatisfying(CommerceSalesException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(CommerceSalesException.NOT_READY);
                    assertThat(e.getStatus().value()).isEqualTo(409);
                });
        // 개인사업자인데 통신판매업 신고번호가 없다
        fillOperator();
        kit.jdbc.update("UPDATE site_operator SET mail_order_number = NULL WHERE id = 1");
        assertThat(sales.state().missing()).contains("OPERATOR_STAGE");
        assertThatThrownBy(() -> sales.toggle(true, "판매 시작합니다", kit.operator, true, "ip")).isInstanceOf(CommerceSalesException.class);
        kit.jdbc.update("UPDATE site_operator SET mail_order_number = '   ' WHERE id = 1");
        assertThat(sales.state().missing()).as("공백만 있는 번호도 없는 것").contains("OPERATOR_STAGE");
        // 법인 + 신고번호 — 켜진다
        fillOperator();
        kit.jdbc.update("UPDATE site_operator SET stage = 'CORPORATION', corporate_number = '110111-0000000' WHERE id = 1");
        assertThat(sales.state().missing()).isEmpty();
        assertThat(sales.toggle(true, "판매 시작합니다", kit.operator, true, "ip").enabled()).isTrue();
    }

    @Test
    void 정본_판매가_켜져_있는_동안_개인_단계로_바꾸거나_통신판매업_신고번호를_비우는_저장은_409() {
        fillOperator();
        // 꺼져 있으면 무엇이든 된다
        inTx(() -> sales.assertOperatorChangeAllowed(SiteOperatorStage.INDIVIDUAL, null));
        inTx(() -> sales.assertOperatorChangeAllowed(SiteOperatorStage.SOLE_PROPRIETOR, null));

        kit.jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = true WHERE id = 1");
        for (Object[] blocked : new Object[][] {{SiteOperatorStage.INDIVIDUAL, null}, {SiteOperatorStage.INDIVIDUAL, "2026-서울-0001"},
                {SiteOperatorStage.SOLE_PROPRIETOR, null}, {SiteOperatorStage.CORPORATION, " "}}) {
            assertThatThrownBy(() -> inTx(() -> sales.assertOperatorChangeAllowed((SiteOperatorStage) blocked[0], (String) blocked[1])))
                    .as("%s / %s", blocked[0], blocked[1])
                    .isInstanceOfSatisfying(CommerceSalesException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(CommerceSalesException.SALES_OPEN);
                        assertThat(e.getStatus().value()).isEqualTo(409);
                    });
        }
        // 사업자 단계 + 신고번호면 바꿔도 된다(번호를 고치거나 법인으로 올리기)
        inTx(() -> sales.assertOperatorChangeAllowed(SiteOperatorStage.SOLE_PROPRIETOR, "2026-서울-0002"));
        inTx(() -> sales.assertOperatorChangeAllowed(SiteOperatorStage.CORPORATION, "2026-서울-0001"));
    }

    @Test
    void 공구_설정은_범위_밖이면_400_바뀐_칸만_전후_감사_휴일_칸은_없다() {
        GroupBuySettingsService settings = kit.settingsService;
        GroupBuySettingsService.Values before = settings.load();
        Map<String, Long> numbers = new LinkedHashMap<>(before.numbers());
        try {
            Map<String, Long> bad = new LinkedHashMap<>(numbers);
            bad.put("holdMinutes", 2L);
            assertThatThrownBy(() -> settings.save(bad, kit.operator, "ip"))
                    .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("values.holdMinutes"));
            Map<String, Long> missing = new LinkedHashMap<>(numbers);
            missing.remove("largeRefundThreshold");
            assertThatThrownBy(() -> settings.save(missing, kit.operator, "ip")).isInstanceOf(ValidationException.class);

            kit.audits.clear();
            Map<String, Long> changed = new LinkedHashMap<>(numbers);
            changed.put("largeRefundThreshold", 0L);
            changed.put("holdMinutes", 7L);
            GroupBuySettingsService.Values after = settings.save(changed, kit.operator, "ip");
            assertThat(after.numbers()).containsEntry("largeRefundThreshold", 0L).containsEntry("holdMinutes", 7L);
            assertThat(kit.groupStore.settings().holdMinutes()).as("업무가 읽는 값도 바로 바뀐다(결정 19)").isEqualTo(7);
            assertThat(kit.audits).singleElement().satisfies(a -> {
                assertThat(a.action()).isEqualTo(AuditActions.GROUPBUY_SETTINGS_UPDATE);
                assertThat(((Map<?, ?>) a.detail().get("after")).keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder("largeRefundThreshold", "holdMinutes");
            });
        } finally {
            settings.save(numbers, kit.operator, "ip");
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
