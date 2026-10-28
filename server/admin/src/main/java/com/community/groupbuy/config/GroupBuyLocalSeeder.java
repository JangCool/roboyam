package com.community.groupbuy.config;

import com.community.batch.config.BatchProperties;
import com.community.commerce.sales.BusinessInfo;
import com.community.commerce.sales.CommerceSalesService;
import com.community.commerce.sales.CommerceSalesStore;
import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.dto.NoticeDraft;
import com.community.groupbuy.dto.ProductDraft;
import com.community.groupbuy.service.GroupBuyAdminService;
import com.community.groupbuy.service.GroupBuyNoticeCatalog;
import com.community.groupbuy.service.GroupBuyNoticeService;
import com.community.groupbuy.service.GroupBuyProductService;
import com.community.groupbuy.service.ShippingPolicyService;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * local 공구 시드(계약 결정 11·87, commerce.md 20절 「local 은 시드가 조건을 실제로 채운다」) — 관리자 서버(배치를 켠 서버)가 뜰 때 한 번.
 *
 * <ul>
 *   <li><b>판매</b>: 시험 사업자 정보(등록번호 체크섬이 맞는 시험 번호) → 운영 체크 항목 여섯 체크 → 켜는 조건 검사({@link CommerceSalesService#state}) 를 통과하면 판매 켜짐.
 *       「local 이면 검사 생략」 코드는 없다 — 조건이 덜 차면 켜지 않고 WARN.</li>
 *   <li><b>배송비 정책</b> {@code STANDARD}(3,000 · 무료 50,000 · 반품 3,000 · 편도), 옵션 종류(색상·사이즈·중량·종류), 카테고리 2단.</li>
 *   <li><b>예시 품목 여덟</b>(결정 87 「예시 품목을 넓힌다」): 햅쌀·사과/감귤·고등어(농수축산물 20) · 김치·소스(가공식품 21) · 티셔츠(의류 1) · 러닝화(구두/신발 2) ·
 *       로봇 키트(소형전자 12) — 상품마다 그 품목의 고시 양식을 다 채우고(신선식품의 날짜 항목은 일반원칙 ④ 대체 표기 예시), 공구를 하나씩 연다:
 *       성사형 모집 중 · 선착순 즉시 발송(옵션 넷) · 마감 임박 · 조기 마감으로 끝난 1차(다음 차수 알림 받기) · 공개된 예정 등.</li>
 *   <li>상품이 하나라도 있으면 상품·공구는 넣지 않는다(재기동 때 다시 넣지 않게). 사업자 정보가 차 있으면 판매 칸도 건너뛴다. dev·prod 에는 없다({@code local} 프로필만).</li>
 * </ul>
 */
@Component
@Profile("local")
public class GroupBuyLocalSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyLocalSeeder.class);
    private static final String IP = "127.0.0.1";

    private final BatchProperties batch;
    private final JdbcTemplate jdbc;
    private final CommerceSalesStore salesStore;
    private final CommerceSalesService sales;
    private final ShippingPolicyService policies;
    private final GroupBuyProductService products;
    private final GroupBuyNoticeService notices;
    private final GroupBuyNoticeCatalog catalog;
    private final GroupBuyAdminService admin;
    private final Clock clock;

    public GroupBuyLocalSeeder(BatchProperties batch, DataSource dataSource, CommerceSalesStore salesStore, CommerceSalesService sales, ShippingPolicyService policies,
                               GroupBuyProductService products, GroupBuyNoticeService notices, GroupBuyNoticeCatalog catalog, GroupBuyAdminService admin,
                               ObjectProvider<Clock> clocks) {
        this.batch = batch;
        this.jdbc = new JdbcTemplate(dataSource);
        this.salesStore = salesStore;
        this.sales = sales;
        this.policies = policies;
        this.products = products;
        this.notices = notices;
        this.catalog = catalog;
        this.admin = admin;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!batch.enabled()) {
            return;
        }
        try {
            seedSales();
            Integer existing = jdbc.queryForObject("SELECT count(*) FROM group_buy_products", Integer.class);
            if (existing == null || existing == 0) {
                seedCatalog();
            }
        } catch (RuntimeException e) {
            log.warn("공구 local 시드를 끝내지 못했다 원인={}", e.getClass().getSimpleName(), e);
        }
    }

    void seedSales() {
        if (!salesStore.businessInfo().companyName().isBlank()) {
            return;
        }
        Instant now = clock.instant();
        salesStore.saveBusinessInfo(new BusinessInfo("로보얌(시험)", "홍길동", "123-45-67891", "2026-서울강남-0001", "서울특별시 강남구", "서울특별시 강남구 테헤란로 1 시험빌딩 3층",
                "02-000-0000", "help@roboyam.example.com", "평일 10:00~17:00(점심 12:00~13:00)", "홍길동(privacy@roboyam.example.com)", "시험 호스팅(주)",
                "배송: 결제 후 발송 예정일에 맞춰 보냅니다. 환불: 배송완료 7일 안 청약철회, 하자·오배송은 3개월 안. 반품 배송비는 상품 상세의 금액을 따릅니다.", null, null), null, now);
        jdbc.update("UPDATE commerce_sales_checklist SET checked = true, checked_at = ?", java.sql.Timestamp.from(now));
        CommerceSalesService.SalesState state = sales.state();
        if (state.canEnable()) {
            salesStore.setSwitch(true, "local 시드", null, now);
            log.info("공구 판매를 local 시드로 켰다");
        } else {
            log.warn("공구 판매 켜기 조건이 덜 찼다(local 시드) 빠진 것={}", state.missing());
        }
    }

    /** 예시 품목·공구 — 부르는 쪽이 「상품이 없을 때만」을 본다 */
    void seedCatalog() {
        Instant now = clock.instant();
        if (policies.history("STANDARD").isEmpty()) {
            policies.schedule(new ShippingPolicyService.PolicyInput("STANDARD", "기본 배송", 3000, 50_000L, 3000, "ONE_WAY", null), null, IP);
        }
        products.saveOptionType(new GroupBuyProductService.OptionTypeDraft("COLOR", "색상", 0, false), null, IP);
        products.saveOptionType(new GroupBuyProductService.OptionTypeDraft("SIZE", "사이즈", 1, false), null, IP);
        products.saveOptionType(new GroupBuyProductService.OptionTypeDraft("WEIGHT", "중량", 2, false), null, IP);
        products.saveOptionType(new GroupBuyProductService.OptionTypeDraft("KIND", "종류", 3, false), null, IP);
        long food = products.createCategory("식품", null, 0, null, IP);
        long rice = products.createCategory("쌀·잡곡", food, 0, null, IP);
        long fruit = products.createCategory("과일", food, 1, null, IP);
        long fish = products.createCategory("수산", food, 2, null, IP);
        long kimchi = products.createCategory("김치·반찬", food, 3, null, IP);
        long sauce = products.createCategory("소스·양념", food, 4, null, IP);
        long fashion = products.createCategory("패션", null, 1, null, IP);
        long clothes = products.createCategory("의류", fashion, 0, null, IP);
        long shoes = products.createCategory("신발", fashion, 1, null, IP);
        long robot = products.createCategory("로봇", null, 2, null, IP);

        long riceP = product(rice, "2026 햅쌀 신동진", 42_000L, List.of(), List.of(sku("10kg", List.of(), 21_000L), sku("20kg", List.of(), 39_000L)), "AGRI_LIVESTOCK_FISHERY");
        long fruitP = product(fruit, "제철 과일 박스", 35_000L, List.of("KIND", "WEIGHT"), List.of(sku(null, List.of("사과", "3kg"), 14_000L),
                sku(null, List.of("사과", "5kg"), 21_000L), sku(null, List.of("감귤", "3kg"), 11_000L), sku(null, List.of("감귤", "5kg"), 17_000L)), "AGRI_LIVESTOCK_FISHERY");
        long fishP = product(fish, "제주 손질 고등어 10팩", 30_000L, List.of(), List.of(sku("10팩", List.of(), 16_000L)), "AGRI_LIVESTOCK_FISHERY");
        long kimchiP = product(kimchi, "전라도 포기김치", 38_000L, List.of(), List.of(sku("3kg", List.of(), 15_000L), sku("5kg", List.of(), 23_000L)), "PROCESSED_FOOD");
        long sauceP = product(sauce, "수제 바비큐 소스 2병", 18_000L, List.of(), List.of(sku("2병", List.of(), 7_000L)), "PROCESSED_FOOD");
        long shirtP = product(clothes, "로보얌 오버핏 티셔츠", 29_000L, List.of("COLOR", "SIZE"), List.of(sku(null, List.of("블랙", "S"), 9_000L),
                sku(null, List.of("블랙", "M"), 9_000L), sku(null, List.of("블랙", "L"), 9_000L), sku(null, List.of("화이트", "M"), 9_000L),
                sku(null, List.of("화이트", "L"), 9_000L)), "CLOTHING");
        long shoeP = product(shoes, "경량 러닝화", 89_000L, List.of("SIZE"), List.of(sku(null, List.of("250"), 38_000L), sku(null, List.of("260"), 38_000L),
                sku(null, List.of("270"), 38_000L), sku(null, List.of("280"), 38_000L)), "SHOES");
        long robotP = product(robot, "휴머노이드 로봇 키트", 1_290_000L, List.of(), List.of(sku("본체", List.of(), 720_000L)), "SMALL_ELECTRONICS");

        Instant past = now.minus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.SECONDS);
        // 성사형 모집 중(마감 후 발주) — 로봇·소스·러닝화
        open(robotP, "휴머노이드 로봇 키트 1차", 990_000, 10, 100, 2, past, past.plus(Duration.ofDays(10)), "AFTER_CLOSE");
        open(sauceP, "수제 바비큐 소스 공구", 14_900, 20, 300, 5, past, past.plus(Duration.ofDays(7)), "AFTER_CLOSE");
        open(shoeP, "경량 러닝화 공구", 69_000, 30, 200, 2, past, past.plus(Duration.ofDays(12)), "AFTER_CLOSE");
        // 선착순 즉시 발송(옵션 넷 — 과일) · 티셔츠(색상 × 사이즈)
        open(fruitP, "제철 과일 박스 선착순", 24_900, 0, 150, 3, past, past.plus(Duration.ofDays(14)), "IMMEDIATE");
        open(shirtP, "오버핏 티셔츠 선착순", 19_900, 0, 120, 4, past, past.plus(Duration.ofDays(20)), "IMMEDIATE");
        // 마감 임박(20시간 뒤) — 햅쌀
        open(riceP, "2026 햅쌀 마감 임박", 34_900, 0, 80, 2, past.minus(Duration.ofDays(2)), now.plus(Duration.ofHours(20)).truncatedTo(ChronoUnit.SECONDS),
                "IMMEDIATE");
        // 끝난 1차(운영자 조기 마감 — 「다음 차수 알림 받기」) — 고등어
        long ended = open(fishP, "제주 고등어 1차", 23_900, 0, 60, 3, past.minus(Duration.ofDays(3)), past.plus(Duration.ofDays(4)), "IMMEDIATE");
        admin.closeEarly(ended, "local 시드 — 끝난 1차 예시", null, IP);
        // 공개된 예정(내일 시작) — 김치
        long upcoming = create(kimchiP, "전라도 포기김치 사전 공개", 29_900, 0, 100, 2, now.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS),
                now.plus(Duration.ofDays(8)).truncatedTo(ChronoUnit.SECONDS), "IMMEDIATE");
        admin.publish(upcoming, version(upcoming), true, null, IP);
        log.info("공구 local 시드 — 상품 8 · 공구 8 을 넣었다");
    }

    private int version(long groupBuyId) {
        Integer v = jdbc.queryForObject("SELECT version FROM group_buys WHERE id = ?", Integer.class, groupBuyId);
        return v == null ? 0 : v;
    }

    private static ProductDraft.SkuDraft sku(String name, List<String> values, long supply) {
        return new ProductDraft.SkuDraft(null, name != null ? name : String.join(" / ", values), values, supply, true); // 옵션 단품 이름 = 값을 「 / 」로 이은 것(화면과 같다)
    }

    private long product(long category, String name, long normalPrice, List<String> optionTypes, List<ProductDraft.SkuDraft> skus, String noticeCategory) {
        long id = products.create(new ProductDraft(category, name, name + " — local 시드 예시 상품", "구성: " + name + "\n보관: 상품 라벨 참고",
                "<p>" + name + " 공동구매 예시 설명입니다. 실제 판매 상품이 아닙니다.</p>", normalPrice, "OFFICIAL_STORE", "local 시드", null, optionTypes, skus, List.of()), null, IP);
        notices.save(id, List.of(fullNotice(noticeCategory, name)), null, IP);
        return id;
    }

    /** 품목의 모든 항목을 채운 기본 양식 — 날짜 항목은 일반원칙 ④ 대체 표기 예시 */
    private NoticeDraft fullNotice(String categoryCode, String title) {
        GroupBuyNoticeCatalog.Category category = catalog.current().category(categoryCode).orElseThrow();
        List<NoticeDraft.Value> values = new ArrayList<>();
        for (GroupBuyNoticeCatalog.Item item : category.items()) {
            String value = switch (item.kind()) {
                case DATE_LIKE -> "제조일·유통기한: 받으신 날로부터 7일 이상 남은 상품을 보냅니다(일반원칙 ④ 대체 표기)";
                case KC_CERT -> "KC 인증번호 R-R-TST-ROBOYAM0001(시험 번호)";
                case QUALITY_WARRANTY -> "소비자분쟁해결기준(공정거래위원회 고시)에 따릅니다";
                default -> item.name() + ": 시험 값(" + title + ")";
            };
            values.add(new NoticeDraft.Value(item.key(), value, false, null, false, null, null));
        }
        return new NoticeDraft(categoryCode, title, List.of(), values);
    }

    private long create(long productId, String title, long price, int min, int max, Integer perUser, Instant start, Instant end, String fulfillment) {
        return admin.create(new GroupBuyDraft(productId, title, price, "STANDARD", min, max, perUser, start, end, "DEMAND", fulfillment,
                "AFTER_CLOSE".equals(fulfillment) ? ServiceTime.dateOf(end).plusDays(7) : null, "IMMEDIATE".equals(fulfillment) ? 3 : null, null, false, List.of(),
                List.of(), true), null, IP);
    }

    /** 만들고 바로 공개 — 시작 시각이 지났으면 그 자리에서 모집 시작 */
    private long open(long productId, String title, long price, int min, int max, Integer perUser, Instant start, Instant end, String fulfillment) {
        long id = create(productId, title, price, min, max, perUser, start, end, fulfillment);
        admin.publish(id, version(id), true, null, IP);
        return id;
    }
}
