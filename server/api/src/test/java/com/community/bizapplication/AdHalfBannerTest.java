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
import com.community.zconfiguration.exception.CommonException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 반 폭 배너 {@code HALF_BANNER}(shared V219 — ad-contract 「반 폭 배너」 사용자 확정 2026-11-15, commerce.md 18절): 홈·가젯 페이지 본문 반 폭 칸 가로 띠 600×100(6:1).
 * 요금표는 띠 배너와 같은 설치 기본값 — 하루 30,000원 · 동시 한도 3(넷째 승인 409 {@code BIZ_SLOT_FULL}) · 최대 14일 · 60일 전부터 신청 · 받기 켬(사업자 신청 가능).
 * 사업자 공개 이름 {@code HALF}(배너 — 이미지·https 링크·대체 글), 금액 = 하루 단가 × 일수 + 부가세 10% 버림. 그림은 6:1 ±3%. 운영자 등록도 광고 종류로 받는다.
 */
class AdHalfBannerTest extends BizApplicationApiTestBase {

    @Autowired
    private BizPriceRuleStore rules;
    @Autowired
    private AdOperatorService ads;
    @Autowired
    private AdAdvertiserService advertisers;

    @Test
    void 요금표_하루_30000원_한도_3_최대_14일_미리_60일_받기_켬() {
        PriceRule rule = rules.find(BizProduct.HALF_BANNER).orElseThrow();
        assertThat(rule.perPost()).isFalse();
        assertThat(rule.unitPrice()).isEqualTo(30_000);
        assertThat(rule.capacity()).isEqualTo(3);
        assertThat(rule.maxDays()).isEqualTo(14);
        assertThat(rule.maxLeadDays()).isEqualTo(60);
        assertThat(rule.accepting()).isTrue();
        assertThat(BizProduct.ofPublic("HALF")).contains(BizProduct.HALF_BANNER);
        assertThat(BizProduct.HALF_BANNER.banner()).isTrue();
        assertThat(BizProduct.HALF_BANNER.adProduct()).isTrue();
        // 사업자 신청 광고 종류 — 작은 배너 다음
        assertThat(members.adProducts()).extracting(p -> p.rule().product()).containsExactly(BizProduct.WIDE_BANNER, BizProduct.STRIP_BANNER,
                BizProduct.SQUARE_BANNER, BizProduct.SMALL_BANNER, BizProduct.HALF_BANNER, BizProduct.WING_BANNER, BizProduct.TEXT_LINE);
    }

    @Test
    void 그림_비율은_6대1_3퍼센트까지() {
        assertThat(BizProduct.HALF_BANNER.bannerRatioOk(600, 100)).isTrue();
        assertThat(BizProduct.HALF_BANNER.bannerRatioOk(1200, 200)).isTrue();
        assertThat(BizProduct.HALF_BANNER.bannerRatioOk(618, 100)).isTrue(); // +3%
        assertThat(BizProduct.HALF_BANNER.bannerRatioOk(582, 100)).isTrue(); // -3%
        assertThat(BizProduct.HALF_BANNER.bannerRatioOk(620, 100)).isFalse(); // +3.3%
        assertThat(BizProduct.HALF_BANNER.bannerRatioOk(1200, 100)).isFalse(); // 띠 12:1
        assertThat(BizProduct.HALF_BANNER.bannerRatioOk(300, 100)).isFalse(); // 작은 3:1
    }

    @Test
    void 사업자_신청_금액은_하루_30000원_곱하기_일수와_부가세_10퍼센트_버림() {
        long user = approvedBusiness();
        long id = submitBanner(user, "HALF", 3, 7, "ONLINE");
        AppRow a = app(id);
        assertThat(a.kind()).isEqualTo(BizApplicationKind.AD_BANNER);
        assertThat(a.quote().supply()).isEqualTo(210_000);
        assertThat(a.quote().vat()).isEqualTo(21_000);
        assertThat(a.quote().total()).isEqualTo(231_000);
        assertThat(store.items(id)).extracting(ItemRow::product).containsExactly(BizProduct.HALF_BANNER);
        assertThat(store.latestCreative(id).orElseThrow().content()).containsKeys("imageFileId", "linkUrl", "altText");
    }

    @Test
    void 사업자_신청은_6대1이_아닌_그림을_400_fields_imageFileId_로_막는다() {
        long user = approvedBusiness();
        LocalDate start = today().plusDays(1);
        assertThat(fieldKeysOf(() -> members.submitAd(user, half(user, start, start.plusDays(2), 1200, 100))))
                .containsExactly(Map.entry("imageFileId", "bizapp.image.ratio"));
        assertThat(members.submitAd(user, half(user, start, start.plusDays(2), 1200, 200))).isPositive();
    }

    @Test
    void 최대_14일을_넘거나_60일_뒤에_시작하면_400_fields_period() {
        long user = approvedBusiness();
        LocalDate today = today();
        assertThat(fieldsOf(() -> members.submitAd(user, half(user, today.plusDays(1), today.plusDays(15), 600, 100)))).contains("period"); // 15일
        assertThat(fieldsOf(() -> members.submitAd(user, half(user, today.plusDays(61), today.plusDays(62), 600, 100)))).contains("period"); // 61일 뒤 시작
        assertThat(members.submitAd(user, half(user, today.plusDays(1), today.plusDays(14), 600, 100))).isPositive(); // 14일
        assertThat(members.submitAd(user, half(user, today.plusDays(60), today.plusDays(61), 600, 100))).isPositive(); // 60일 뒤 시작
    }

    @Test
    void 같은_기간_세_건까지_승인되고_넷째는_409_BIZ_SLOT_FULL() {
        BizPeriods.Period p = freshPeriod(3);
        for (int i = 0; i < 3; i++) {
            long id = submitBanner(approvedBusiness(), "HALF", 0, 3, "ONLINE");
            approve(id, "ONLINE", p);
        }
        long fourth = submitBanner(approvedBusiness(), "HALF", 0, 3, "ONLINE");
        assertThat(codeOf(() -> approve(fourth, "ONLINE", p))).isEqualTo("BIZ_SLOT_FULL");
        // 다른 상품의 자리는 따로다 — 같은 기간의 띠 배너는 그대로 승인된다
        long strip = submitBanner(approvedBusiness(), "STRIP", 0, 3, "ONLINE");
        approve(strip, "ONLINE", p);
    }

    @Test
    void 운영자_등록도_반_폭_배너를_광고_종류로_받는다() {
        String adv = advertisers.create(operator, new AdAdvertiserService.Create("반폭광고주" + ids.nextId(), "김담당", "010-1234-5678", "h" + ids.nextId() + "@example.com",
                null, null)).id();
        long file = ids.nextId();
        jdbc.update("INSERT INTO files (id, purpose, storage_key, content_type, size_bytes, width, height, status, uploaded_by, created_at) "
                + "VALUES (?, 'BIZ_AD_IMAGE', ?, 'image/png', 100, 600, 100, 'PENDING', ?, now())", file, "biz-ad/op/test/" + file + ".png", operator.operatorId());
        BizPeriods.Period p = freshPeriod(2);
        long id = ads.createManual(operator, new AdOperatorService.Manual(adv, "HALF_BANNER", p.start().toString(), p.end().toString(), null,
                new AdOperatorService.Creative(Long.toString(file), null, "https://shop.example.com/half", "반 폭 배너"), 60_000L, List.of(), null));
        assertThat(app(id).kind()).isEqualTo(BizApplicationKind.AD_BANNER);
        assertThat(store.items(id)).extracting(ItemRow::product).containsExactly(BizProduct.HALF_BANNER);
        assertThat(store.items(id).get(0).supply()).isEqualTo(60_000); // 요금표 2일
    }

    private BizAdApplyRequest half(long user, LocalDate start, LocalDate end, int width, int height) {
        return new BizAdApplyRequest("HALF", start.toString(), end.toString(), Long.toString(adImage(user, width, height)), "https://a.example.com/x", "반 폭", null, "ONLINE",
                POLICY, POLICY);
    }

    private static Map<String, String> fieldKeysOf(Executable call) {
        try {
            call.execute();
        } catch (CommonException e) {
            return e.getFields().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, x -> x.getValue().key()));
        } catch (Throwable e) {
            throw new AssertionError("업무 예외가 아니다: " + e, e);
        }
        throw new AssertionError("예외가 나지 않았다");
    }
}
