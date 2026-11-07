package com.community.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import com.community.testsupport.SiteOperatorFixture;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 운영 주체 설정 API 의 커머스 규칙(terms-operator-contract 세션 정리 결정 — 사업자 정보의 정본 = 운영 주체, commerce.md 18절 「규칙은 테스트로」).
 * 실제 관리자 앱 문맥 + 보안 필터 + PostgreSQL. 판매 스위치·운영 주체는 1행 표라 시험마다 처음 상태로 되돌린다.
 */
class SiteOperatorCommerceHttpTest extends Commerce04AdminTestBase {

    private static final String SOLE = "{\"stage\":\"SOLE_PROPRIETOR\",\"operatorName\":\"홍길동\",\"contactEmail\":\"help@test.local\",\"phone\":\"02-000-0000\","
            + "\"privacyOfficerSameAsOperator\":true,\"privacyOfficerName\":null,\"privacyOfficerEmail\":null,\"hostingProvider\":\"시험 호스팅\","
            + "\"business\":{\"businessName\":\"시험상회\",\"representative\":\"홍길동\",\"registrationNumber\":\"123-45-67891\",\"address\":\"서울특별시 강남구 1\","
            + "\"mailOrderNumber\":%s,\"mailOrderAuthority\":\"서울특별시 강남구\",\"corporateNumber\":null}}";
    private static final String INDIVIDUAL = "{\"stage\":\"INDIVIDUAL\",\"operatorName\":\"홍길동\",\"contactEmail\":\"help@test.local\",\"phone\":null,"
            + "\"privacyOfficerSameAsOperator\":true,\"hostingProvider\":null,\"business\":{\"businessName\":\"남은상회\",\"representative\":\"홍길동\","
            + "\"registrationNumber\":\"123-45-67891\",\"address\":\"주소\",\"mailOrderNumber\":\"1\",\"mailOrderAuthority\":null,\"corporateNumber\":null}}";

    @AfterEach
    void restore() {
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");
        SiteOperatorFixture.clear(jdbc);
    }

    private static String sole(String mailOrderNumber) {
        return SOLE.formatted(mailOrderNumber == null ? "null" : "\"" + mailOrderNumber + "\"");
    }

    private int historyCount() {
        return jdbc.queryForObject("SELECT count(*) FROM site_operator_history", Integer.class);
    }

    private int auditCount(long operatorId, String action) {
        return auditLogs.where().actor(operatorId).action(action).count();
    }

    @Test
    void 판매가_켜져_있는_동안_개인_단계로_바꾸거나_통신판매업_신고번호를_비우는_저장은_409_값은_그대로() throws Exception {
        SiteOperatorFixture.fillBusiness(jdbc);
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = true WHERE id = 1");
        long manager = operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE);
        Cookie session = login(manager);

        mvc.perform(withCsrf(put("/api/admin/site/operator"), session).content(INDIVIDUAL)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COMMERCE_SALES_OPEN"));
        mvc.perform(withCsrf(put("/api/admin/site/operator"), session).content(sole(null))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COMMERCE_SALES_OPEN"));
        mvc.perform(withCsrf(put("/api/admin/site/operator"), session).content(sole(" "))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT stage FROM site_operator WHERE id = 1", String.class)).isEqualTo("SOLE_PROPRIETOR");
        assertThat(jdbc.queryForObject("SELECT mail_order_number FROM site_operator WHERE id = 1", String.class)).isEqualTo("2026-서울-0001");

        // 사업자 단계 + 신고번호면 판매 중에도 저장된다
        mvc.perform(withCsrf(put("/api/admin/site/operator"), session).content(sole("2026-서울-0002"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.business.mailOrderNumber").value("2026-서울-0002"));

        // 판매를 끄면 개인 단계로 바꿀 수 있고 사업자 칸은 비워진다
        jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");
        mvc.perform(withCsrf(put("/api/admin/site/operator"), session).content(INDIVIDUAL)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("INDIVIDUAL")).andExpect(jsonPath("$.data.business").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT business_name FROM site_operator WHERE id = 1", String.class)).isNull();
    }

    /** 사용자 결정 2026-11-07 — 로보티콘 유료 판매도 같은 조건. 켜져 있는 동안 개인 전환·신고번호 비우기는 409(공구와 다른 코드), 끄면 된다 */
    @Test
    void 로보티콘_유료_판매가_켜져_있는_동안_개인_단계로_바꾸거나_신고번호를_비우는_저장은_409_EMOTICON_PAID_SALES_OPEN() throws Exception {
        Boolean paidWas = jdbc.queryForObject("SELECT paid_sales_enabled FROM emoticon_settings WHERE id = 1", Boolean.class);
        SiteOperatorFixture.fillBusiness(jdbc);
        jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = true WHERE id = 1");
        try {
            Cookie session = login(operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE));
            mvc.perform(withCsrf(put("/api/admin/site/operator"), session).header("Accept-Language", "ko").content(INDIVIDUAL)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("EMOTICON_PAID_SALES_OPEN"))
                    .andExpect(jsonPath("$.error.message").value("로보티콘 유료 판매가 켜져 있어 운영 주체를 개인 단계로 바꾸거나 통신판매업 신고번호를 비울 수 없어요. "
                            + "먼저 로보티콘 설정에서 유료 판매를 꺼 주세요."));
            mvc.perform(withCsrf(put("/api/admin/site/operator"), session).content(sole(null))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("EMOTICON_PAID_SALES_OPEN"));
            assertThat(jdbc.queryForObject("SELECT stage FROM site_operator WHERE id = 1", String.class)).isEqualTo("SOLE_PROPRIETOR");
            assertThat(jdbc.queryForObject("SELECT mail_order_number FROM site_operator WHERE id = 1", String.class)).isEqualTo("2026-서울-0001");

            // 공구 판매도 켜져 있으면 공구 코드가 먼저
            jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = true WHERE id = 1");
            mvc.perform(withCsrf(put("/api/admin/site/operator"), session).content(INDIVIDUAL)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("COMMERCE_SALES_OPEN"));
            jdbc.update("UPDATE commerce_sales_settings SET groupbuy_open = false WHERE id = 1");

            // 사업자 단계 + 신고번호는 켜진 채로도 저장된다
            mvc.perform(withCsrf(put("/api/admin/site/operator"), session).content(sole("2026-서울-0003"))).andExpect(status().isOk());

            // 유료 판매를 끄면 개인 단계로 바꿀 수 있다
            jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = false WHERE id = 1");
            mvc.perform(withCsrf(put("/api/admin/site/operator"), session).content(INDIVIDUAL)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.stage").value("INDIVIDUAL"));
        } finally {
            jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = ? WHERE id = 1", paidWas);
        }
    }

    /** 사용자 결정 2026-11-07 — 개인 단계면 로보티콘 유료 판매를 켤 수 없다(400), 조회는 paidSalesAllowed 로 알린다 */
    @Test
    void 로보티콘_설정_조회의_paidSalesAllowed와_개인_단계의_유료_판매_켜기_400() throws Exception {
        Boolean paidWas = jdbc.queryForObject("SELECT paid_sales_enabled FROM emoticon_settings WHERE id = 1", Boolean.class);
        jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = false WHERE id = 1");
        try {
            Cookie session = login(operatorWith(Permission.EMOTICON_READ, Permission.EMOTICON_SETTINGS));
            mvc.perform(get("/api/admin/emoticons/settings").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.paidSalesAllowed").value(false));
            String on = "{\"rewardPerAcquire\":10,\"rewardDailyCap\":500,\"itemMin\":8,\"itemMax\":40,\"pendingLimit\":3,\"feeRatePercent\":30,\"settlementDay\":10,"
                    + "\"payoutMinimum\":10000,\"refundDays\":7,\"paidSalesEnabled\":true,\"priceTiers\":[1500,2500,3500]}";
            mvc.perform(withCsrf(put("/api/admin/emoticons/settings"), session).header("Accept-Language", "ko").content(on)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.error.fields.paidSalesEnabled").value("운영 주체가 개인사업자 이상이고 통신판매업 신고번호가 있어야 유료 판매를 켤 수 있어요. "
                            + "설정 › 운영·약관 › 운영 주체에서 바꿔 주세요."));

            SiteOperatorFixture.fillBusiness(jdbc);
            mvc.perform(get("/api/admin/emoticons/settings").cookie(session)).andExpect(jsonPath("$.data.paidSalesAllowed").value(true));
            mvc.perform(withCsrf(put("/api/admin/emoticons/settings"), session).content(on)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.paidSalesEnabled").value(true)).andExpect(jsonPath("$.data.paidSalesAllowed").value(true));
        } finally {
            jdbc.update("UPDATE emoticon_settings SET paid_sales_enabled = ? WHERE id = 1", paidWas);
        }
    }

    @Test
    void 커머스_사업자_정보_읽기는_운영_주체_값을_보여_주고_단계_전환은_이력과_감사_같은_값은_아무것도_남기지_않는다() throws Exception {
        long manager = operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE, Permission.COMMERCE_SETTINGS);
        Cookie session = login(manager);
        int history = historyCount();

        mvc.perform(withCsrf(put("/api/admin/site/operator"), session).content(sole("2026-서울-0001"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("SOLE_PROPRIETOR")).andExpect(jsonPath("$.data.history[0].toStage").value("SOLE_PROPRIETOR"))
                .andExpect(jsonPath("$.data.history[0].changedBy").value(operatorName(manager)));
        assertThat(historyCount()).isEqualTo(history + 1);
        assertThat(auditCount(manager, "SITE_OPERATOR_STAGE_CHANGE")).isEqualTo(1);
        String detail = auditLogs.where().actor(manager).action("SITE_OPERATOR_STAGE_CHANGE").single().detail();
        assertThat(detail).as("감사에 이메일 원문을 남기지 않는다").doesNotContain("help@test.local").contains("he***@test.local");

        // 커머스 관리자 읽기 = 운영 주체 값(겹치는 칸)
        mvc.perform(get("/api/admin/commerce/business-info").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.values.companyName").value("시험상회")).andExpect(jsonPath("$.data.values.businessNo").value("123-45-67891"))
                .andExpect(jsonPath("$.data.values.mailOrderNo").value("2026-서울-0001")).andExpect(jsonPath("$.data.values.privacyOfficer").value("홍길동"));

        // 같은 값 — 감사·이력 없음
        mvc.perform(withCsrf(put("/api/admin/site/operator"), session).content(sole("2026-서울-0001"))).andExpect(status().isOk());
        assertThat(historyCount()).isEqualTo(history + 1);
        assertThat(auditCount(manager, "SITE_OPERATOR_STAGE_CHANGE") + auditCount(manager, "SITE_OPERATOR_UPDATE")).isEqualTo(1);

        // 칸만 바뀜 — SITE_OPERATOR_UPDATE
        mvc.perform(withCsrf(put("/api/admin/site/operator"), session).content(sole("2026-서울-0009"))).andExpect(status().isOk());
        assertThat(auditCount(manager, "SITE_OPERATOR_UPDATE")).isEqualTo(1);
        assertThat(historyCount()).isEqualTo(history + 1);
    }

    @Test
    void 검사는_칸마다_모아_400_사업자_칸은_business_열쇠_권한은_site_read_site_manage() throws Exception {
        Cookie reader = login(operatorWith(Permission.SITE_READ));
        mvc.perform(get("/api/admin/site/operator").cookie(reader)).andExpect(status().isOk()).andExpect(jsonPath("$.data.stage").exists());
        mvc.perform(withCsrf(put("/api/admin/site/operator"), reader).content(sole("1"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/site/operator").cookie(login(operatorWith()))).andExpect(status().isForbidden());

        Cookie manager = login(operatorWith(Permission.SITE_READ, Permission.SITE_MANAGE));
        String bad = "{\"stage\":\"SOLE_PROPRIETOR\",\"operatorName\":\"" + "가".repeat(31) + "\",\"contactEmail\":\"not-mail\",\"phone\":null,"
                + "\"privacyOfficerSameAsOperator\":false,\"privacyOfficerName\":null,\"privacyOfficerEmail\":null,"
                + "\"business\":{\"businessName\":\"\",\"representative\":\"홍길동\",\"registrationNumber\":\"1234567891\",\"address\":\"주소\"}}";
        mvc.perform(withCsrf(put("/api/admin/site/operator"), manager).content(bad)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields.operatorName").exists()).andExpect(jsonPath("$.error.fields.contactEmail").exists())
                .andExpect(jsonPath("$.error.fields.phone").exists()).andExpect(jsonPath("$.error.fields.privacyOfficerName").exists())
                .andExpect(jsonPath("$.error.fields.privacyOfficerEmail").exists())
                .andExpect(jsonPath("$.error.fields['business.businessName']").exists())
                .andExpect(jsonPath("$.error.fields['business.registrationNumber']").exists())
                .andExpect(jsonPath("$.error.fields['business.representative']").doesNotExist());
        mvc.perform(withCsrf(put("/api/admin/site/operator"), manager).content("{\"stage\":\"SOLE_PROPRIETOR\",\"operatorName\":\"홍\",\"contactEmail\":\"a@b.co\","
                        + "\"phone\":\"02-000-0000 abc\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.phone").exists()).andExpect(jsonPath("$.error.fields['business.address']").exists());
    }
}
