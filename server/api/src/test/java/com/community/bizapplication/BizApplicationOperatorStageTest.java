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
 * 광고·핫딜 유료 신청 × 운영 주체 단계(사용자 결정 2026-11-07 — 돈을 받는 기능은 모두 {@code CommerceSalesStore.operatorCanSell()} = 개인사업자 이상 + 통신판매업 신고번호,
 * commerce.md 18절 「규칙은 테스트로」). 사이트 스위치가 없어 그때그때 판정한다: 새 신청 409 {@code BIZ_PRODUCT_CLOSED}(상품의 {@code accepting} false) · 온라인 결제 시작 409
 * {@code BIZ_PAYMENT_UNAVAILABLE}(이미 결제창에 들어간 결제의 완료는 받는다) · 유료 승인(ONLINE·MANUAL)·입금 확정 409 {@code BIZ_OPERATOR_STAGE}. 개인사업자 + 신고번호면 모두 통과.
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
    void 개인_단계면_새_신청은_409_BIZ_PRODUCT_CLOSED_상품_accepting은_false_신고번호_없는_개인사업자도_같다() throws Exception {
        long user = approvedBusiness();
        dealBoard();
        individual();
        assertThat(codeOf(() -> submitTextLine(user, 0, 3, "ONLINE"))).isEqualTo("BIZ_PRODUCT_CLOSED");
        assertThat(codeOf(() -> submitBanner(user, "WIDE", 0, 3, "MANUAL"))).isEqualTo("BIZ_PRODUCT_CLOSED");
        LocalDate start = today().plusDays(2);
        assertThat(codeOf(() -> submitDeal(user, "NONE", null, null, "ONLINE"))).isEqualTo("BIZ_PRODUCT_CLOSED");
        assertThat(codeOf(() -> submitDeal(user, "HOME_TOP", start, start.plusDays(2), "ONLINE"))).isEqualTo("BIZ_PRODUCT_CLOSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_applications WHERE user_id = ?", Integer.class, user)).isZero();
        mvc.perform(as(user, get("/api/business/ad-products"))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].accepting").value(false))
                .andExpect(jsonPath("$.data[?(@.accepting == true)]").isEmpty());
        mvc.perform(as(user, get("/api/business/deal-products"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.accepting").value(false))
                .andExpect(jsonPath("$.data.placements[0].accepting").value(false));

        seller();
        jdbc.update("UPDATE site_operator SET mail_order_number = NULL WHERE id = 1");
        assertThat(codeOf(() -> submitTextLine(user, 0, 3, "ONLINE"))).isEqualTo("BIZ_PRODUCT_CLOSED");
        mvc.perform(as(user, get("/api/business/ad-products"))).andExpect(jsonPath("$.data[0].accepting").value(false));

        // 개인사업자 + 신고번호면 받는다
        seller();
        mvc.perform(as(user, get("/api/business/ad-products"))).andExpect(jsonPath("$.data[0].accepting").value(true));
        mvc.perform(as(user, get("/api/business/deal-products"))).andExpect(jsonPath("$.data.accepting").value(true))
                .andExpect(jsonPath("$.data.placements[0].accepting").value(true));
        assertThat(app(submitTextLine(user, 0, 3, "ONLINE")).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(app(submitDeal(user, "NONE", null, null, "ONLINE")).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
    }

    @Test
    void 개인_단계면_유료_승인은_ONLINE_MANUAL_모두_409_BIZ_OPERATOR_STAGE_심사_대기_그대로() {
        long user = approvedBusiness();
        long online = submitTextLine(user, 0, 3, "ONLINE");
        long manual = submitTextLine(user, 0, 3, "MANUAL");
        BizPeriods.Period p = freshPeriod(3);
        BizPeriods.Period q = freshPeriod(3);
        individual();
        assertThat(codeOf(() -> approve(online, "ONLINE", p))).isEqualTo("BIZ_OPERATOR_STAGE");
        assertThat(codeOf(() -> approve(manual, "MANUAL", q))).isEqualTo("BIZ_OPERATOR_STAGE");
        assertThat(app(online).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(app(manual).status()).isEqualTo(BizApplicationStatus.SUBMITTED);
        assertThat(app(online).approved()).as("금액 스냅샷도 남기지 않는다").isNull();

        seller();
        approve(online, "ONLINE", p);
        approve(manual, "MANUAL", q);
        assertThat(app(online).status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        assertThat(app(manual).status()).isEqualTo(BizApplicationStatus.AWAITING_DEPOSIT);
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
    void 개인_단계면_수동_입금_확정은_409_BIZ_OPERATOR_STAGE_입금_대기_그대로() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 3, "MANUAL");
        approve(id, "MANUAL", freshPeriod(3));
        review.uploadContracts(operator, id, java.util.List.of(new com.community.bizapplication.dto.BizUpload("계약서.pdf", pdf())), null);
        review.saveDeposit(operator, id, app(id).approved().total(), today().toString(), "시험상사", null);

        individual();
        assertThat(codeOf(() -> review.confirm(operator, id))).isEqualTo("BIZ_OPERATOR_STAGE");
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.AWAITING_DEPOSIT);

        seller();
        review.confirm(operator, id);
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
    }
}
