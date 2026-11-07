package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.service.BizPeriods;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentCheckout;
import com.community.testsupport.SiteOperatorFixture;
import com.community.user.entity.Role;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 광고·핫딜 유료 신청 × 운영 주체 단계(commerce.md 18절 「규칙은 테스트로」). 판정은 {@code CommerceSalesStore.operatorCanSell()}(개인사업자 이상 + 통신판매업 신고번호)이고,
 * 개인 단계에서 막는 것은 <b>온라인 결제</b>뿐이다(사용자 결정 2026-11-07 오후 — 개인도 계약서 + 입금으로 광고를 받는다): 온라인 결제 신청 {@code 400 fields.paymentMethod}
 * (상품 {@code onlinePayment} false, {@code accepting} 은 요금표 그대로) · 온라인 승인 409 {@code BIZ_OPERATOR_STAGE} · 온라인 결제 시작 409 {@code BIZ_PAYMENT_UNAVAILABLE}
 * (이미 결제창에 들어간 결제의 완료는 받는다). 수동 결제 신청·승인·입금 확정은 단계와 상관없다.
 */
class BizApplicationOperatorStageTest extends BizApplicationApiTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUpMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).contentType(MediaType.APPLICATION_JSON);
    }

    /** 개인 단계(처음 값) */
    private void individual() {
        SiteOperatorFixture.clear(jdbc);
    }

    /** 개인사업자 + 신고번호 */
    private void seller() {
        SiteOperatorFixture.fillBusiness(jdbc);
    }

    @Test
    void 개인_단계면_온라인_결제_신청만_400_fields_paymentMethod_수동_결제_신청은_받고_상품은_onlinePayment_false() throws Exception {
        long user = approvedBusiness();
        dealBoard();
        individual();
        LocalDate start = today().plusDays(2);
        assertThat(fieldsOf(() -> submitTextLine(user, 0, 3, "ONLINE"))).containsExactly("paymentMethod");
        assertThat(fieldsOf(() -> submitDeal(user, "HOME_TOP", start, start.plusDays(2), "ONLINE"))).containsExactly("paymentMethod");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_applications WHERE user_id = ?", Integer.class, user)).isZero();
        assertThat(app(submitBanner(user, "WIDE", 0, 3, "MANUAL")).status()).as("수동 결제는 받는다").isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(app(submitDeal(user, "NONE", null, null, "MANUAL")).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        mvc.perform(as(user, get("/api/business/ad-products"))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].onlinePayment").value(false))
                .andExpect(jsonPath("$.data[?(@.accepting == true)]").isNotEmpty());
        mvc.perform(as(user, get("/api/business/deal-products"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.onlinePayment").value(false))
                .andExpect(jsonPath("$.data.accepting").value(true)).andExpect(jsonPath("$.data.placements[0].accepting").value(true));

        seller();
        jdbc.update("UPDATE site_operator SET mail_order_number = NULL WHERE id = 1");
        assertThat(fieldsOf(() -> submitTextLine(user, 0, 3, "ONLINE"))).as("신고번호 없는 개인사업자도 같다").containsExactly("paymentMethod");

        // 개인사업자 + 신고번호면 온라인 결제도 받는다
        seller();
        mvc.perform(as(user, get("/api/business/ad-products"))).andExpect(jsonPath("$.data[0].onlinePayment").value(true));
        mvc.perform(as(user, get("/api/business/deal-products"))).andExpect(jsonPath("$.data.onlinePayment").value(true));
        assertThat(app(submitTextLine(user, 0, 3, "ONLINE")).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(app(submitDeal(user, "NONE", null, null, "ONLINE")).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
    }

    @Test
    void 개인_단계면_온라인_승인만_409_BIZ_OPERATOR_STAGE_수동_승인은_된다() {
        long user = approvedBusiness();
        long online = submitTextLine(user, 0, 3, "ONLINE");
        long manual = submitTextLine(user, 0, 3, "MANUAL");
        BizPeriods.Period p = freshPeriod(3);
        BizPeriods.Period q = freshPeriod(3);
        individual();
        assertThat(codeOf(() -> approve(online, "ONLINE", p))).isEqualTo("BIZ_OPERATOR_STAGE");
        assertThat(app(online).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(app(online).approved()).as("금액 스냅샷도 남기지 않는다").isNull();
        approve(manual, "MANUAL", q);
        assertThat(app(manual).status()).as("수동 승인은 개인 단계도 된다").isEqualTo(BizApplicationStatus.AWAITING_DEPOSIT);
        approve(online, "MANUAL", p);
        assertThat(app(online).status()).as("온라인 희망 건도 수동으로 바꿔 승인").isEqualTo(BizApplicationStatus.AWAITING_DEPOSIT);
    }

    @Test
    void 개인_단계면_온라인_결제_시작은_409_BIZ_PAYMENT_UNAVAILABLE_이미_결제창에_들어간_결제의_완료는_받는다() {
        long user = approvedBusiness();
        long first = submitTextLine(user, 0, 3, "ONLINE");
        long second = submitTextLine(user, 0, 3, "ONLINE");
        approve(first, "ONLINE", freshPeriod(3));
        approve(second, "ONLINE", freshPeriod(3));
        PaymentCheckout opened = members.startPayment(user, first);

        individual();
        assertThat(codeOf(() -> members.startPayment(user, second))).isEqualTo("BIZ_PAYMENT_UNAVAILABLE");
        assertThat(codeOf(() -> members.startPayment(user, first))).as("열린 결제창을 다시 받는 것도 막는다").isEqualTo("BIZ_PAYMENT_UNAVAILABLE");
        assertThat(app(second).status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        // 이미 결제창에 들어간 결제는 완료를 받는다(돈은 이미 냈다)
        fakePg.pay(opened.paymentId(), opened.amount(), "KRW", clock.instant());
        confirm.confirm(opened.paymentId(), ConfirmedVia.COMPLETE_API);
        assertThat(app(first).status()).isEqualTo(BizApplicationStatus.SCHEDULED);

        seller();
        pay(user, second);
        assertThat(app(second).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
    }

    @Test
    void 개인_단계도_수동_입금_확정은_된다() {
        long user = approvedBusiness();
        individual();
        long id = submitTextLine(user, 0, 3, "MANUAL");
        approve(id, "MANUAL", freshPeriod(3));
        review.uploadContracts(operator, id, java.util.List.of(new com.community.bizapplication.dto.BizUpload("계약서.pdf", pdf())), null);
        review.saveDeposit(operator, id, app(id).approved().total(), today().toString(), "시험상사", null);

        review.confirm(operator, id);
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
    }
}
