package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.dto.request.BizDealApplyRequest;
import com.community.bizapplication.dto.request.BizDealInfo;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizPriceRuleStore;
import com.community.bizapplication.service.BizApplicationReader;
import com.community.bizapplication.service.BizPeriods;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사업자 신청(광고·핫딜) — 자격 판정 한 곳(미승인·재심사 409), 정책 동의 없음 400, 기간 규칙 400, 신청 때 자리 판정(409 {@code BIZ_SLOT_FULL}), 받지 않는 상품 409,
 * 요금표 계산(하루 단가 × 일수 + 부가세 버림), 핫딜 글 + 노출 한 신청 건, 단축 URL 차단, 다른 회원의 건 404.
 */
class BizApplicationSubmitTest extends BizApplicationApiTestBase {

    @Autowired
    private BizApplicationReader reader;
    @Autowired
    private BizPriceRuleStore rules;

    @Test
    void 자격_판정_승인되지_않은_사업자는_409_BUSINESS_NOT_APPROVED_재심사_중이면_409_BUSINESS_RE_REVIEW() {
        long none = newMember();
        long pending = business("PENDING", false);
        long suspended = business("SUSPENDED", false);
        long reReview = business("PENDING", true);
        assertThat(codeOf(() -> submitTextLine(none, 0, 3, "ONLINE"))).isEqualTo("BUSINESS_NOT_APPROVED");
        assertThat(codeOf(() -> submitTextLine(pending, 0, 3, "ONLINE"))).isEqualTo("BUSINESS_NOT_APPROVED");
        assertThat(codeOf(() -> submitTextLine(suspended, 0, 3, "ONLINE"))).isEqualTo("BUSINESS_NOT_APPROVED");
        assertThat(codeOf(() -> submitTextLine(reReview, 0, 3, "ONLINE"))).isEqualTo("BUSINESS_RE_REVIEW");
        assertThat(codeOf(() -> submitDeal(reReview, "NONE", null, null, "ONLINE"))).isEqualTo("BUSINESS_RE_REVIEW");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_applications WHERE user_id IN (?, ?, ?, ?)", Integer.class, none, pending, suspended, reReview)).isZero();
    }

    @Test
    void 서비스_줄이_승인이_아니면_그_서비스만_막힌다() {
        long user = approvedBusiness();
        jdbc.update("UPDATE business_services SET status = 'SUSPENDED' WHERE profile_id = ? AND service = 'HOTDEAL'", profileOf(user));
        assertThat(codeOf(() -> submitDeal(user, "NONE", null, null, "ONLINE"))).isEqualTo("BUSINESS_NOT_APPROVED");
        assertThat(submitTextLine(user, 0, 3, "ONLINE")).isPositive();
    }

    @Test
    void 정책_동의_버전이_비면_400_fields_agree() {
        long user = approvedBusiness();
        LocalDate start = today().plusDays(2);
        assertThat(fieldsOf(() -> members.submitAd(user, new BizAdApplyRequest("TEXT_LINE", start.toString(), start.plusDays(2).toString(), null, "https://a.example.com",
                null, "한 줄", "ONLINE", null, POLICY)))).containsExactly("agree");
        assertThat(fieldsOf(() -> members.submitDeal(user, new BizDealApplyRequest("제목", deal("NONE"), null, null, "ONLINE", POLICY, " ")))).containsExactly("agree");
    }

    @Test
    void 기간_규칙_시작은_내일부터_미리_신청_일수_안_최대_일수까지_어기면_400_fields_period() {
        long user = approvedBusiness();
        LocalDate today = today();
        assertThat(fieldsOf(() -> members.submitAd(user, ad("TEXT_LINE", today, today.plusDays(1))))).contains("period");
        assertThat(fieldsOf(() -> members.submitAd(user, ad("TEXT_LINE", today.plusDays(61), today.plusDays(62))))).contains("period");
        assertThat(fieldsOf(() -> members.submitAd(user, ad("TEXT_LINE", today.plusDays(1), today.plusDays(31))))).contains("period"); // 31일 > 30일
        assertThat(fieldsOf(() -> members.submitAd(user, ad("TEXT_LINE", today.plusDays(3), today.plusDays(2))))).contains("period");
        assertThat(members.submitAd(user, ad("TEXT_LINE", today.plusDays(1), today.plusDays(30)))).isPositive();
    }

    @Test
    void 금액은_하루_단가_곱하기_일수와_부가세_10퍼센트_버림이고_기간은_한국_날짜_반열림() {
        long user = approvedBusiness();
        long id = submitBanner(user, "WIDE", 3, 7, "ONLINE");
        AppRow a = app(id);
        assertThat(a.kind()).isEqualTo(BizApplicationKind.AD_BANNER);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(a.quote().supply()).isEqualTo(50_000L * 7);
        assertThat(a.quote().vat()).isEqualTo(35_000);
        assertThat(a.quote().total()).isEqualTo(385_000);
        assertThat(a.startAt()).isEqualTo(BizPeriods.startOf(today().plusDays(4)));
        assertThat(a.endAt()).isEqualTo(BizPeriods.startOf(today().plusDays(11)));
        assertThat(a.companyName()).isEqualTo("시험상사");
        assertThat(a.bizNoLast4()).isEqualTo("6789");
        assertThat(a.adPolicyVersion()).isEqualTo(POLICY);
        assertThat(store.items(id)).extracting(ItemRow::product).containsExactly(BizProduct.WIDE_BANNER);
        assertThat(store.latestCreative(id).orElseThrow().version()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM files WHERE uploaded_by_user = ? AND purpose = 'BIZ_AD_IMAGE'", String.class, user)).isEqualTo("IN_USE");
    }

    @Test
    void 핫딜_글에_노출을_고르면_한_신청_건에_게시_줄과_노출_줄이_같이_있다() {
        long user = approvedBusiness();
        LocalDate start = today().plusDays(2);
        long id = submitDeal(user, "CATEGORY_TOP", start, start.plusDays(2), "MANUAL");
        AppRow a = app(id);
        assertThat(a.kind()).isEqualTo(BizApplicationKind.HOTDEAL_POST);
        List<ItemRow> items = store.items(id);
        assertThat(items).extracting(ItemRow::product).containsExactlyInAnyOrder(BizProduct.HOTDEAL_POST, BizProduct.HOTDEAL_CATEGORY);
        assertThat(a.quote().supply()).isEqualTo(30_000 + 10_000L * 3);
        assertThat(BizApplicationReader.capacityProduct(items)).isEqualTo(BizProduct.HOTDEAL_CATEGORY);
        // 노출 없음 — 내일부터 7일, 게시 줄 하나
        long plain = submitDeal(user, "NONE", null, null, "ONLINE");
        assertThat(store.items(plain)).extracting(ItemRow::product).containsExactly(BizProduct.HOTDEAL_POST);
        assertThat(app(plain).startAt()).isEqualTo(BizPeriods.startOf(today().plusDays(1)));
        assertThat(app(plain).endAt()).isEqualTo(BizPeriods.startOf(today().plusDays(8)));
        assertThat(app(plain).quote().total()).isEqualTo(33_000);
    }

    @Test
    void 핫딜_링크가_단축_URL_이면_400_fields_deal_linkUrl() {
        long user = approvedBusiness();
        BizDealInfo shortLink = new BizDealInfo("https://bit.ly/x", "몰", null, 1000L, null, null, "UNKNOWN", "본문", "NONE");
        assertThat(fieldsOf(() -> members.submitDeal(user, new BizDealApplyRequest("제목", shortLink, null, null, "ONLINE", POLICY, POLICY)))).contains("deal.linkUrl");
    }

    @Test
    void 신청_때도_자리를_판정해_꽉_찼으면_409_BIZ_SLOT_FULL() {
        // 넓은 배너 한도 2 — 이 시험만 쓰는 가까운 기간(오늘+55~56)에 승인된 두 건이 자리를 차지한다
        LocalDate s = today().plusDays(55);
        BizPeriods.Period p = BizPeriods.ofDates(s, s.plusDays(1));
        for (int i = 0; i < 2; i++) {
            long other = approvedBusiness();
            long id = submitBanner(other, "WIDE", 54, 2, "ONLINE");
            approve(id, "ONLINE", p);
        }
        long user = approvedBusiness();
        assertThat(codeOf(() -> submitBanner(user, "WIDE", 54, 2, "ONLINE"))).isEqualTo("BIZ_SLOT_FULL");
        // 끝 = 다음 시작이면 겹치지 않는다(반열림) — 바로 다음 날부터는 신청된다
        assertThat(submitBanner(user, "WIDE", 56, 2, "ONLINE")).isPositive();
    }

    @Test
    void 받지_않는_상품은_409_BIZ_PRODUCT_CLOSED() {
        long user = approvedBusiness();
        rules.setAccepting(BizProduct.SMALL_BANNER, false);
        try {
            assertThat(codeOf(() -> submitBanner(user, "SMALL", 0, 2, "ONLINE"))).isEqualTo("BIZ_PRODUCT_CLOSED");
        } finally {
            rules.setAccepting(BizProduct.SMALL_BANNER, true);
        }
    }

    @Test
    void 다른_회원의_신청_건은_404_BIZ_APPLICATION_NOT_FOUND() {
        long owner = approvedBusiness();
        long other = approvedBusiness();
        long id = submitTextLine(owner, 0, 3, "ONLINE");
        assertThat(codeOf(() -> reader.snapshotOwned(id, other))).isEqualTo("BIZ_APPLICATION_NOT_FOUND");
        assertThat(codeOf(() -> members.cancel(other, id))).isEqualTo("BIZ_APPLICATION_NOT_FOUND");
        assertThat(codeOf(() -> members.startPayment(other, id))).isEqualTo("BIZ_APPLICATION_NOT_FOUND");
        assertThat(codeOf(() -> members.requestRefund(other, id, "환불해 주세요"))).isEqualTo("BIZ_APPLICATION_NOT_FOUND");
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
    }

    private static BizAdApplyRequest ad(String type, LocalDate start, LocalDate end) {
        return new BizAdApplyRequest(type, start.toString(), end.toString(), null, "https://a.example.com/x", null, "한 줄 광고", "ONLINE", POLICY, POLICY);
    }
}
