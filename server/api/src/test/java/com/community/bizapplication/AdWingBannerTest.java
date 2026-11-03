package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.ad.service.AdAdvertiserService;
import com.community.ad.service.AdOperatorService;
import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizPriceRuleStore;
import com.community.bizapplication.repository.master.BizPriceRuleStore.PriceRule;
import com.community.bizapplication.service.BizPeriods;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 날개 배너 {@code WING_BANNER}(V116 — ad-contract 「공개 노출 목업」 확정 결정 2026-11-01, commerce.md 18절): 요금표 하루 30,000원 · 동시 한도 4(다섯째 승인 409
 * {@code BIZ_SLOT_FULL}) · 최대 30일 · 60일 전부터 신청 · 받기 켬. 사업자 공개 이름 {@code WING}(배너 — 이미지·https 링크·대체 글), 금액 = 하루 단가 × 일수 + 부가세 10% 버림.
 * 운영자 등록도 광고 종류로 받는다.
 */
class AdWingBannerTest extends BizApplicationApiTestBase {

    @Autowired
    private BizPriceRuleStore rules;
    @Autowired
    private AdOperatorService ads;
    @Autowired
    private AdAdvertiserService advertisers;

    @Test
    void 요금표_하루_30000원_한도_4_최대_30일_미리_60일_받기_켬() {
        PriceRule rule = rules.find(BizProduct.WING_BANNER).orElseThrow();
        assertThat(rule.perPost()).isFalse();
        assertThat(rule.unitPrice()).isEqualTo(30_000);
        assertThat(rule.capacity()).isEqualTo(4);
        assertThat(rule.maxDays()).isEqualTo(30);
        assertThat(rule.maxLeadDays()).isEqualTo(60);
        assertThat(rule.accepting()).isTrue();
        assertThat(BizProduct.ofPublic("WING")).contains(BizProduct.WING_BANNER);
        assertThat(BizProduct.WING_BANNER.banner()).isTrue();
        assertThat(BizProduct.WING_BANNER.adProduct()).isTrue();
        assertThat(members.adProducts()).extracting(p -> p.rule().product()).containsExactly(BizProduct.WIDE_BANNER, BizProduct.STRIP_BANNER,
                BizProduct.SQUARE_BANNER, BizProduct.SMALL_BANNER, BizProduct.WING_BANNER, BizProduct.TEXT_LINE);
    }

    @Test
    void 사업자_신청_금액은_하루_30000원_곱하기_일수와_부가세_10퍼센트_버림() {
        long user = approvedBusiness();
        long id = submitBanner(user, "WING", 3, 7, "ONLINE");
        AppRow a = app(id);
        assertThat(a.kind()).isEqualTo(BizApplicationKind.AD_BANNER);
        assertThat(a.quote().supply()).isEqualTo(210_000);
        assertThat(a.quote().vat()).isEqualTo(21_000);
        assertThat(a.quote().total()).isEqualTo(231_000);
        assertThat(store.items(id)).extracting(ItemRow::product).containsExactly(BizProduct.WING_BANNER);
        assertThat(store.latestCreative(id).orElseThrow().content()).containsKeys("imageFileId", "linkUrl", "altText");
    }

    @Test
    void 최대_30일을_넘거나_60일_뒤에_시작하면_400_fields_period() {
        long user = approvedBusiness();
        LocalDate today = today();
        assertThat(fieldsOf(() -> members.submitAd(user, wing(user, today.plusDays(1), today.plusDays(31))))).contains("period"); // 31일
        assertThat(fieldsOf(() -> members.submitAd(user, wing(user, today.plusDays(61), today.plusDays(62))))).contains("period"); // 61일 뒤 시작
        assertThat(members.submitAd(user, wing(user, today.plusDays(1), today.plusDays(30)))).isPositive(); // 30일
        assertThat(members.submitAd(user, wing(user, today.plusDays(60), today.plusDays(61)))).isPositive(); // 60일 뒤 시작
    }

    @Test
    void 같은_기간_네_건까지_승인되고_다섯째는_409_BIZ_SLOT_FULL() {
        BizPeriods.Period p = freshPeriod(3);
        for (int i = 0; i < 4; i++) {
            long id = submitBanner(approvedBusiness(), "WING", 0, 3, "ONLINE");
            approve(id, "ONLINE", p);
        }
        long fifth = submitBanner(approvedBusiness(), "WING", 0, 3, "ONLINE");
        assertThat(codeOf(() -> approve(fifth, "ONLINE", p))).isEqualTo("BIZ_SLOT_FULL");
        // 다른 상품의 자리는 따로다 — 같은 기간의 작은 배너는 그대로 승인된다
        long small = submitBanner(approvedBusiness(), "SMALL", 0, 3, "ONLINE");
        approve(small, "ONLINE", p);
    }

    @Test
    void 운영자_등록도_날개_배너를_광고_종류로_받는다() {
        String adv = advertisers.create(operator, new AdAdvertiserService.Create("날개광고주" + ids.nextId(), "김담당", "010-1234-5678", "w" + ids.nextId() + "@example.com",
                null, null)).id();
        long file = ids.nextId();
        jdbc.update("INSERT INTO files (id, purpose, storage_key, content_type, size_bytes, width, height, status, uploaded_by, created_at) "
                + "VALUES (?, 'BIZ_AD_IMAGE', ?, 'image/png', 100, 160, 600, 'PENDING', ?, now())", file, "biz-ad/op/test/" + file + ".png", operator.operatorId());
        BizPeriods.Period p = freshPeriod(2);
        long id = ads.createManual(operator, new AdOperatorService.Manual(adv, "WING_BANNER", p.start().toString(), p.end().toString(), null,
                new AdOperatorService.Creative(Long.toString(file), null, "https://shop.example.com/wing", "날개 배너"), 60_000L, List.of(), null));
        assertThat(app(id).kind()).isEqualTo(BizApplicationKind.AD_BANNER);
        assertThat(store.items(id)).extracting(ItemRow::product).containsExactly(BizProduct.WING_BANNER);
        assertThat(store.items(id).get(0).supply()).isEqualTo(60_000); // 요금표 2일
    }

    private BizAdApplyRequest wing(long user, LocalDate start, LocalDate end) {
        return new BizAdApplyRequest("WING", start.toString(), end.toString(), Long.toString(adImage(user, 160, 600)), "https://a.example.com/x", "날개", null, "ONLINE", POLICY, POLICY);
    }
}
