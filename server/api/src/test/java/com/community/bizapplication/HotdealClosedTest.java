package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.dto.BizMemberView;
import com.community.bizapplication.dto.request.BizDealApplyRequest;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.service.MyBizApplicationService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사업자 핫딜 신청 닫음(V121 — 사용자 2026-11-01 「닫아 둬」, biz-application-contract): 예약 뒤 게시 고리({@code BizHotdealPosting})가 생길 때까지 글 게시
 * {@code HOTDEAL_POST}·노출 자리 {@code HOTDEAL_TOP}·{@code HOTDEAL_CATEGORY} 의 받기가 꺼져 신청은 409 {@code BIZ_PRODUCT_CLOSED}(신청 건이 생기지 않는다).
 * 광고 상품은 그대로 받는다. 사업자 화면 응답은 글 게시·노출 자리 각각 {@code accepting} 을 싣는다.
 */
class HotdealClosedTest extends BizApplicationApiTestBase {

    @Autowired
    private MyBizApplicationService view;

    @Test
    void V121_뒤_핫딜_세_상품은_받기_꺼짐_광고_상품은_켜짐() {
        for (BizProduct p : HOTDEAL_PRODUCTS) {
            assertThat(priceRules.find(p).orElseThrow().accepting()).as(p.name()).isFalse();
        }
        assertThat(members.adProducts()).extracting(i -> i.rule().accepting()).hasSize(6).containsOnly(true);
    }

    @Test
    void 사업자_핫딜_게시_신청은_노출_없이도_노출을_골라도_409_BIZ_PRODUCT_CLOSED_신청_건이_생기지_않는다() {
        long user = approvedBusiness();
        LocalDate start = today().plusDays(2);
        assertThat(codeOf(() -> rawSubmitDeal(user, "NONE", null, null))).isEqualTo("BIZ_PRODUCT_CLOSED");
        assertThat(codeOf(() -> rawSubmitDeal(user, "HOME_TOP", start, start.plusDays(1)))).isEqualTo("BIZ_PRODUCT_CLOSED");
        assertThat(codeOf(() -> rawSubmitDeal(user, "CATEGORY_TOP", start, start.plusDays(1)))).isEqualTo("BIZ_PRODUCT_CLOSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_applications WHERE user_id = ?", Integer.class, user)).isZero();
    }

    @Test
    void 글_게시만_열려_있으면_노출_없는_신청은_받고_닫힌_노출을_고르면_409() {
        long user = approvedBusiness();
        LocalDate start = today().plusDays(2);
        priceRules.setAccepting(BizProduct.HOTDEAL_POST, true);
        try {
            assertThat(codeOf(() -> rawSubmitDeal(user, "HOME_TOP", start, start.plusDays(1)))).isEqualTo("BIZ_PRODUCT_CLOSED");
            assertThat(rawSubmitDeal(user, "NONE", null, null)).isPositive();
        } finally {
            priceRules.setAccepting(BizProduct.HOTDEAL_POST, false);
        }
    }

    @Test
    void 광고_상품은_그대로_받는다() {
        long user = approvedBusiness();
        assertThat(submitTextLine(user, 0, 3, "ONLINE")).isPositive();
        assertThat(submitBanner(user, "WIDE", 0, 2, "ONLINE")).isPositive();
    }

    @Test
    void 사업자_화면_핫딜_요금_응답은_글_게시와_노출_자리_각각_accepting_false() {
        BizMemberView.DealProduct product = view.dealProduct();
        assertThat(product.postPrice()).isEqualTo(30_000);
        assertThat(product.accepting()).isFalse();
        assertThat(product.placements()).extracting(BizMemberView.DealPlacement::placement).containsExactly("HOME_TOP", "CATEGORY_TOP");
        assertThat(product.placements()).extracting(BizMemberView.DealPlacement::accepting).containsOnly(false);
        // 요금표 받기를 따라간다 — 하나만 켜면 그 자리만 true
        priceRules.setAccepting(BizProduct.HOTDEAL_TOP, true);
        try {
            BizMemberView.DealProduct reopened = view.dealProduct();
            assertThat(reopened.accepting()).isFalse();
            assertThat(reopened.placements()).extracting(BizMemberView.DealPlacement::accepting).containsExactly(true, false);
        } finally {
            priceRules.setAccepting(BizProduct.HOTDEAL_TOP, false);
        }
        assertThat(view.adProducts()).extracting(BizMemberView.AdProduct::accepting).containsOnly(true);
    }

    /** 받기를 열지 않고 바로 신청(바탕의 {@code submitDeal} 은 잠깐 연다) */
    private long rawSubmitDeal(long user, String placement, LocalDate start, LocalDate end) {
        return members.submitDeal(user, new BizDealApplyRequest("[시험상사] 텀블러 1+1", deal(placement),
                start == null ? null : start.toString(), end == null ? null : end.toString(), "ONLINE", POLICY, POLICY));
    }
}
