package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.ad.service.AdAdvertiserService;
import com.community.ad.service.AdOperatorService;
import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.dto.request.BizCreativeRequest;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.service.BizPeriods;
import com.community.zconfiguration.exception.CommonException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 배너 그림 비율 검사(docs/plans/biz-ad-hotdeal/02-ad.md 미디어 업로드 · ad-contract 「배너 비율 검사」 2026-11-03) — 규격(넓은 1200×300 · 띠 1200×100 ·
 * 네모 300×250 · 작은 300×100 · 날개 160×600)과 ±3% 넘게 다르면 400 {@code fields.imageFileId}(운영자 등록은 {@code creative.imageFileId}) =
 * {@code bizapp.image.ratio}. 사업자 신청 · 소재 바꾸기(새로 올린 그림만) · 운영자 수동 등록. 크기를 모르는 옛 파일은 막지 않는다.
 */
class AdBannerRatioTest extends BizApplicationApiTestBase {

    @Autowired
    AdOperatorService ads;
    @Autowired
    AdAdvertiserService advertisers;

    @Test
    void 규격_비율_판정은_3퍼센트까지_맞고_넘으면_다르다() {
        assertThat(BizProduct.WIDE_BANNER.bannerRatioOk(1200, 300)).isTrue();
        assertThat(BizProduct.WIDE_BANNER.bannerRatioOk(1236, 300)).isTrue(); // +3%
        assertThat(BizProduct.WIDE_BANNER.bannerRatioOk(1164, 300)).isTrue(); // -3%
        assertThat(BizProduct.WIDE_BANNER.bannerRatioOk(1240, 300)).isFalse(); // +3.3%
        assertThat(BizProduct.WIDE_BANNER.bannerRatioOk(1000, 600)).isFalse();
        assertThat(BizProduct.STRIP_BANNER.bannerRatioOk(2400, 200)).isTrue();
        assertThat(BizProduct.SQUARE_BANNER.bannerRatioOk(600, 500)).isTrue();
        assertThat(BizProduct.SQUARE_BANNER.bannerRatioOk(500, 500)).isFalse();
        assertThat(BizProduct.SMALL_BANNER.bannerRatioOk(600, 200)).isTrue();
        assertThat(BizProduct.WING_BANNER.bannerRatioOk(160, 600)).isTrue();
        assertThat(BizProduct.WING_BANNER.bannerRatioOk(300, 600)).isFalse();
        // 크기를 모르는 파일 · 배너가 아닌 상품은 보지 않는다
        assertThat(BizProduct.WIDE_BANNER.bannerRatioOk(null, null)).isTrue();
        assertThat(BizProduct.TEXT_LINE.bannerRatioOk(10, 10)).isTrue();
    }

    @Test
    void 사업자_신청은_규격과_다른_비율의_그림을_막는다() {
        long user = approvedBusiness();
        LocalDate start = today().plusDays(1);
        long wrong = adImage(user, 1000, 600);
        assertThat(fieldKeysOf(() -> members.submitAd(user, apply("WIDE", start, wrong)))).containsExactly(Map.entry("imageFileId", "bizapp.image.ratio"));
        // 다른 종류의 규격(띠 12:1)으로 낸 넓은 배너 그림도 막는다
        long strip = adImage(user, 1200, 100);
        assertThat(fieldKeysOf(() -> members.submitAd(user, apply("WIDE", start, strip)))).containsExactly(Map.entry("imageFileId", "bizapp.image.ratio"));
        // ±3% 안이면 받는다
        long id = members.submitAd(user, apply("WIDE", start, adImage(user, 1230, 300)));
        assertThat(app(id)).isNotNull();
    }

    @Test
    void 소재_바꾸기는_새로_올린_그림만_비율을_본다() {
        long user = approvedBusiness();
        long id = submitBanner(user, "SMALL", 0, 2, "ONLINE");
        long wrong = adImage(user, 300, 250);
        assertThat(fieldKeysOf(() -> members.changeCreative(user, id, new BizCreativeRequest(Long.toString(wrong), "https://shop.example.com/new", "새 배너", null, null, null))))
                .containsExactly(Map.entry("imageFileId", "bizapp.image.ratio"));
        // 그림을 다시 올리지 않으면(지금 그림 그대로) 링크·대체 글만 바꿀 수 있다 — 규칙 전에 붙은 그림이어도
        jdbc.update("UPDATE files SET width = 10, height = 10 WHERE id = (SELECT (content ->> 'imageFileId')::bigint FROM biz_creative_versions WHERE application_id = ? ORDER BY version DESC LIMIT 1)", id);
        members.changeCreative(user, id, new BizCreativeRequest(null, "https://shop.example.com/link-only", "새 대체 글", null, null, null));
        members.changeCreative(user, id, new BizCreativeRequest(Long.toString(adImage(user, 600, 200)), "https://shop.example.com/new", "새 배너", null, null, null));
    }

    @Test
    void 운영자_수동_등록도_규격과_다른_비율의_그림을_막는다() {
        String adv = advertisers.create(operator, new AdAdvertiserService.Create("비율광고주" + ids.nextId(), "김담당", "010-1234-5678", "ratio" + ids.nextId() + "@example.com", null, null)).id();
        BizPeriods.Period p = freshPeriod(2);
        String wrong = operatorImage(300, 600);
        assertThat(fieldKeysOf(() -> ads.createManual(operator, new AdOperatorService.Manual(adv, "WIDE_BANNER", p.start().toString(), p.end().toString(), 5,
                new AdOperatorService.Creative(wrong, null, "https://a.example.com", "운영자 배너"), 100_000L, List.of(), null))))
                .containsExactly(Map.entry("creative.imageFileId", "bizapp.image.ratio"));
        String wing = operatorImage(160, 600);
        assertThat(ads.createManual(operator, new AdOperatorService.Manual(adv, "WING_BANNER", p.start().toString(), p.end().toString(), 5,
                new AdOperatorService.Creative(wing, null, "https://a.example.com", "날개 배너"), 100_000L, List.of(), null))).isPositive();
    }

    private BizAdApplyRequest apply(String adType, LocalDate start, long imageId) {
        return new BizAdApplyRequest(adType, start.toString(), start.plusDays(1).toString(), Long.toString(imageId), "https://shop.example.com/b", "배너", null, "ONLINE",
                POLICY, POLICY);
    }

    /** 운영자가 올린 광고 이미지(파일 장부 한 줄) — 크기를 정해서 */
    private String operatorImage(int width, int height) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO files (id, purpose, storage_key, content_type, size_bytes, width, height, status, uploaded_by, created_at) "
                + "VALUES (?, 'BIZ_AD_IMAGE', ?, 'image/png', 100, ?, ?, 'PENDING', ?, now())", id, "biz-ad/op/test/" + id + ".png", width, height, operator.operatorId());
        return Long.toString(id);
    }

    /** 칸 오류의 칸 → 메시지 키 */
    private static Map<String, String> fieldKeysOf(Executable call) {
        try {
            call.execute();
        } catch (CommonException e) {
            return e.getFields().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, x -> x.getValue().key()));
        } catch (Throwable e) {
            throw new AssertionError("업무 예외가 아니다: " + e, e);
        }
        throw new AssertionError("예외가 나지 않았다");
    }
}
