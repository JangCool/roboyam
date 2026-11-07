package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.service.BizApplicationMemberService;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 관리자 추가 환불(클레임 — biz-pause-contract 「추가 환불(클레임)」, V128) — 실제 관리자 앱 문맥 + PostgreSQL.
 * {@code POST /api/admin/biz-applications/{id}/refund/claim} 권한 {@code deal-order.refund}(도메인 처리 권한만으로는 403) · 400 칸 · 409 · 응답 {@code refundSummary}
 * (신청 건 상세·광고 일정표 상세) · 수동 송금 대기 → 송금 완료 · 감사 MONEY.
 */
class BizRefundClaimAdminHttpTest extends Commerce04AdminTestBase {

    /** 유료 신청 승인·입금 확정은 운영 주체가 개인사업자 이상 + 통신판매업 신고번호일 때만(사용자 결정 2026-11-07) */
    @Override
    protected boolean sellingOperator() {
        return true;
    }

    @Autowired
    private BizApplicationMemberService members;

    private long approvedBusiness() {
        long user = newMember();
        long profile = ids.nextId();
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at) "
                + "VALUES (?, ?, 'APPROVED', false, '클레임상사', 'x', ?, '1234', 'x', 'x', 'x', '06236', 'x', 'v1', 'v1', now(), now(), now(), now())", profile, user,
                String.format("%064x", profile));
        for (String service : new String[] {"AD", "HOTDEAL"}) {
            jdbc.update("INSERT INTO business_services (id, profile_id, service, status, updated_at) VALUES (?, ?, ?, 'APPROVED', now())", ids.nextId(), profile, service);
        }
        return user;
    }

    /**
     * 수동 결제 한 줄 광고(입금 확인 11,000원) — 기간을 {@code fromDays}~{@code toDays}(지금 기준 일)로 맞춘다. 승인·확정 흐름은 다른 시험이 막으므로 상태만 맞춘다
     */
    private long manualText(int fromDays, int toDays) {
        long user = approvedBusiness();
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(3);
        long id = members.submitAd(user, new BizAdApplyRequest("TEXT_LINE", start.toString(), start.plusDays(2).toString(), null, "https://a.example.com", null,
                "클레임 시험 한 줄", "MANUAL", "v1", BizApplicationMemberService.REFUND_POLICY_VERSION));
        long op = operatorWith();
        String range = "start_at = date_trunc('minute', now()) + make_interval(days => ?), end_at = date_trunc('minute', now()) + make_interval(days => ?)";
        jdbc.update("UPDATE biz_applications SET status = 'SCHEDULED', payment_method = 'MANUAL', approved_supply = 10000, approved_vat = 1000, approved_total = 11000, "
                + "approved_at = now(), approved_by = ?, active_contract_count = 1, deposit_confirmed_at = now(), " + range + " WHERE id = ?", op, fromDays, toDays, id);
        jdbc.update("UPDATE biz_application_items SET " + range + " WHERE application_id = ?", fromDays, toDays, id);
        jdbc.update("INSERT INTO biz_deposits (application_id, amount, deposited_on, depositor_name, confirmed_by, confirmed_at) VALUES (?, 11000, current_date, '클레임상사', ?, now())",
                id, op);
        return id;
    }

    private static String body(Object amount, String reason) {
        return "{\"amount\":" + amount + ",\"reason\":" + (reason == null ? "null" : "\"" + reason + "\"") + "}";
    }

    @Test
    void 권한은_deal_order_refund_이고_도메인_처리_권한만으로는_403() throws Exception {
        long id = manualText(-4, -1);
        String path = "/api/admin/biz-applications/" + id + "/refund/claim";
        Cookie manager = login(operatorWith(Permission.AD_READ, Permission.AD_MANAGE, Permission.AD_RESTORE, Permission.AD_TAKEDOWN));
        mvc.perform(withCsrf(post(path), manager).content(body(1000, "고객 클레임 보상"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        mvc.perform(withCsrf(post(path), login(operatorWith(Permission.AD_READ))).content(body(1000, "고객 클레임 보상"))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_refunds WHERE application_id = ?", Integer.class, id)).isZero();
        mvc.perform(withCsrf(post(path), login(operatorWith(Permission.DEAL_ORDER_REFUND))).content(body(1000, "고객 클레임 보상"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refund.type").value("CLAIM"));
    }

    @Test
    void 금액과_사유는_400_진행_중은_409_수동은_송금_대기_뒤_송금_완료와_합계_감사() throws Exception {
        Cookie refunder = login(operatorWith(Permission.DEAL_ORDER_REFUND));
        long running = manualText(-1, 2);
        mvc.perform(withCsrf(post("/api/admin/biz-applications/" + running + "/refund/claim"), refunder).content(body(1000, "고객 클레임 보상")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("BIZ_APPLICATION_STATE_CHANGED"));

        long id = manualText(-4, -1);
        String base = "/api/admin/biz-applications/" + id;
        mvc.perform(withCsrf(post(base + "/refund/claim"), refunder)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.amount").exists()).andExpect(jsonPath("$.error.fields.reason").exists());
        for (String bad : new String[] {"0", "-5", "null", "11001"}) {
            mvc.perform(withCsrf(post(base + "/refund/claim"), refunder).content(body(bad, "고객 클레임 보상"))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.fields.amount").exists());
        }
        mvc.perform(withCsrf(post(base + "/refund/claim"), refunder).content(body(1000, "넷글자임"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.reason").exists()).andExpect(jsonPath("$.error.fields.length()").value(1));
        mvc.perform(get(base).cookie(refunder)).andExpect(status().isForbidden()); // 상세 조회는 도메인 조회 권한

        Cookie viewer = login(operatorWith(Permission.AD_READ, Permission.DEAL_ORDER_REFUND));
        mvc.perform(get(base).cookie(viewer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.refundSummary.paid").value(11000))
                .andExpect(jsonPath("$.data.refundSummary.refunded").value(0)).andExpect(jsonPath("$.data.refundSummary.refundable").value(11000))
                .andExpect(jsonPath("$.data.refundSummary.feeKept").value(0));

        mvc.perform(withCsrf(post(base + "/refund/claim"), viewer).content(body(4000, "고객 클레임 일부 보상"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED")).andExpect(jsonPath("$.data.refund.type").value("CLAIM"))
                .andExpect(jsonPath("$.data.refund.status").value("APPROVED")).andExpect(jsonPath("$.data.refund.approvedAmount").value(4000))
                .andExpect(jsonPath("$.data.refund.method").value("MANUAL")).andExpect(jsonPath("$.data.refund.transferStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.refund.decisionReason").value("고객 클레임 일부 보상")).andExpect(jsonPath("$.data.refund.feeRate").doesNotExist())
                .andExpect(jsonPath("$.data.refundSummary.refunded").value(4000)).andExpect(jsonPath("$.data.refundSummary.refundable").value(7000))
                .andExpect(jsonPath("$.data.history[?(@.action == 'CLAIM_REFUNDED')].note").value(org.hamcrest.Matchers.contains("4000")));
        // 광고 일정표 상세에도 같은 합계
        mvc.perform(get("/api/admin/ads/" + id).cookie(viewer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.refundSummary.refundable").value(7000))
                .andExpect(jsonPath("$.data.refund.type").value("CLAIM"));
        // 송금 대기가 남아 있으면 다음 추가 환불 409 → 송금 완료 표시 뒤 나머지
        mvc.perform(withCsrf(post(base + "/refund/claim"), viewer).content(body(1000, "고객 클레임 추가 보상"))).andExpect(status().isConflict());
        mvc.perform(withCsrf(post(base + "/refund/transfer"), viewer).content("{\"note\":\"국민은행 이체\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refund.transferStatus").value("DONE"));
        mvc.perform(withCsrf(post(base + "/refund/claim"), viewer).content(body(7001, "고객 클레임 나머지 보상"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.amount").exists());
        mvc.perform(withCsrf(post(base + "/refund/claim"), viewer).content(body(7000, "고객 클레임 나머지 보상"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundSummary.refunded").value(11000)).andExpect(jsonPath("$.data.refundSummary.refundable").value(0));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id = ? AND action = 'BIZ_APP_REFUND_CLAIM' AND retention_class = 'MONEY'", Integer.class,
                Long.toString(id))).isEqualTo(2);
        assertThat(AuditRetention.classify(AuditActions.BIZ_APP_REFUND_CLAIM)).isEqualTo(AuditRetentionClass.MONEY);
    }

    @Test
    void 중단_환불_승인_뒤_합계의_feeKept_는_수수료_합이고_추가_환불로_줄어든다() throws Exception {
        long id = manualText(-1, 2);
        Cookie manager = login(operatorWith(Permission.AD_READ, Permission.AD_MANAGE, Permission.DEAL_ORDER_REFUND));
        String base = "/api/admin/biz-applications/" + id;
        mvc.perform(withCsrf(post(base + "/stop"), manager).content("{\"channel\":\"PHONE\",\"memo\":\"중단 요청\"}")).andExpect(status().isOk());
        long proposed = jdbc.queryForObject("SELECT proposed_amount FROM biz_refunds WHERE application_id = ? AND status = 'REQUESTED'", Long.class, id);
        long fee = jdbc.queryForObject("SELECT fee_amount FROM biz_refunds WHERE application_id = ? AND status = 'REQUESTED'", Long.class, id);
        assertThat(fee).isPositive();
        mvc.perform(withCsrf(post(base + "/refund/claim"), manager).content(body(fee, "열린 환불 중"))).andExpect(status().isConflict());
        mvc.perform(withCsrf(post(base + "/refund/approve"), manager).content("{\"amount\":" + proposed + ",\"reason\":\"제안대로\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundSummary.refunded").value(proposed)).andExpect(jsonPath("$.data.refundSummary.refundable").value(11000 - proposed))
                .andExpect(jsonPath("$.data.refundSummary.feeKept").value(fee));
        mvc.perform(withCsrf(post(base + "/refund/transfer"), manager)).andExpect(status().isOk());
        mvc.perform(withCsrf(post(base + "/refund/claim"), manager).content(body(fee, "수수료까지 돌려 달라는 클레임"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED")).andExpect(jsonPath("$.data.refundSummary.feeKept").value(0))
                .andExpect(jsonPath("$.data.refundSummary.refundable").value(11000 - proposed - fee));
    }
}
