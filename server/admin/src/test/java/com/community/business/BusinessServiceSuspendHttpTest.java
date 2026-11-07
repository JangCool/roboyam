package com.community.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.business.service.BusinessPii;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 사업자 서비스만 정지·해제(business-contract 「서비스만 정지·해제」, 화면 확정 2026-11-02) — 실제 관리자 앱 문맥 + PostgreSQL.
 * {@code POST /api/admin/businesses/{id}/services/{service}/suspend}(본문 = 신원 정지, {@code business.manage}) · {@code .../resume}(본문 = 신원 해제, {@code business.unsuspend}).
 * 신원 승인 · 줄 상태가 맞을 때만(아니면 409), 그 줄만 바뀌고 다른 서비스는 그대로, 처리 기록(+ 서비스) · 감사 · 사업자 알림. 신원 해제는 서비스만 정지된 줄을 되살리지 않는다.
 * 신청 건 처리(그 서비스 건에만)는 api {@code BizServiceSuspendTest} 가 막는다.
 */
class BusinessServiceSuspendHttpTest extends Commerce04AdminTestBase {

    private static final String SUSPEND = "{\"code\":\"POLICY_VIOLATION\",\"text\":\"허위 가격 반복\",\"pendingAction\":\"KEEP\",\"scheduledAction\":\"HOLD\","
            + "\"runningAction\":\"TAKEDOWN_ONLY\"}";

    @Autowired
    private BusinessPii pii;

    private Cookie manager;
    private long managerId;

    @BeforeEach
    void operators() throws Exception {
        managerId = operatorWith(Permission.BUSINESS_READ, Permission.BUSINESS_MANAGE, Permission.BUSINESS_UNSUSPEND);
        manager = login(managerId);
    }

    @Test
    void 핫딜만_정지하면_그_줄만_정지_사유와_처리_기록_감사_사업자_알림_광고는_그대로() throws Exception {
        long[] b = business();
        long id = b[0];
        // 칸 검사 · 모르는 서비스 404 · 권한
        call(id, "HOTDEAL", "suspend", "{}", manager).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.code").exists())
                .andExpect(jsonPath("$.error.fields.text").exists());
        call(id, "SHOP", "suspend", SUSPEND, manager).andExpect(status().isNotFound());
        Cookie reader = login(operatorWith(Permission.BUSINESS_READ));
        call(id, "HOTDEAL", "suspend", SUSPEND, reader).andExpect(status().isForbidden());

        call(id, "hotdeal", "suspend", SUSPEND, manager).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.serviceCards[?(@.service == 'HOTDEAL')].status").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.serviceCards[?(@.service == 'HOTDEAL')].suspendCode").value("POLICY_VIOLATION"))
                .andExpect(jsonPath("$.data.serviceCards[?(@.service == 'HOTDEAL')].suspendText").value("허위 가격 반복"))
                .andExpect(jsonPath("$.data.serviceCards[?(@.service == 'HOTDEAL')].suspendedByName").exists())
                .andExpect(jsonPath("$.data.serviceCards[?(@.service == 'HOTDEAL')].impact.running").value(0))
                .andExpect(jsonPath("$.data.serviceCards[?(@.service == 'AD')].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.history[0].action").value("SERVICE_SUSPENDED"))
                .andExpect(jsonPath("$.data.history[0].service").value("HOTDEAL"));
        assertThat(line(id, "HOTDEAL")).containsEntry("status", "SUSPENDED").containsEntry("suspend_code", "POLICY_VIOLATION").containsEntry("suspended_by", managerId);
        assertThat(line(id, "AD")).containsEntry("status", "APPROVED").containsEntry("suspend_code", null);
        assertThat(jdbc.queryForObject("SELECT status FROM business_profiles WHERE id = ?", String.class, id)).isEqualTo("APPROVED");
        assertThat(audits(managerId, "BUSINESS_SERVICE_SUSPEND")).isEqualTo(1);
        assertThat(memberKinds(b[1], "HOTDEAL_SUSPENDED")).isEqualTo(1);
        // 이미 정지 → 409
        call(id, "HOTDEAL", "suspend", SUSPEND, manager).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("BUSINESS_STATE_CHANGED"));
    }

    @Test
    void 신원_정지_해제는_서비스만_정지된_줄을_되살리지_않고_서비스_해제가_따로_푼다() throws Exception {
        long[] b = business();
        long id = b[0];
        call(id, "HOTDEAL", "suspend", SUSPEND, manager).andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/admin/businesses/" + id + "/suspend"), manager).content(SUSPEND)).andExpect(status().isOk());
        assertThat(line(id, "AD")).containsEntry("status", "SUSPENDED").containsEntry("suspend_code", null);
        // 신원이 정지면 서비스 해제는 409(신원 정지가 우선)
        call(id, "HOTDEAL", "resume", "{\"restore\":\"EXTEND_RESTORE\"}", manager).andExpect(status().isConflict());
        mvc.perform(withCsrf(post("/api/admin/businesses/" + id + "/unsuspend"), manager).content("{\"restore\":\"EXTEND_RESTORE\"}")).andExpect(status().isOk());
        assertThat(line(id, "AD")).containsEntry("status", "APPROVED");
        assertThat(line(id, "HOTDEAL")).containsEntry("status", "SUSPENDED").containsEntry("suspend_code", "POLICY_VIOLATION");

        call(id, "HOTDEAL", "resume", "{}", manager).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.restore").exists());
        call(id, "AD", "resume", "{\"restore\":\"EXTEND_RESTORE\"}", manager).andExpect(status().isConflict()); // 광고는 서비스만 정지가 아니다
        Cookie noUnsuspend = login(operatorWith(Permission.BUSINESS_READ, Permission.BUSINESS_MANAGE));
        call(id, "HOTDEAL", "resume", "{\"restore\":\"EXTEND_RESTORE\"}", noUnsuspend).andExpect(status().isForbidden());
        call(id, "HOTDEAL", "resume", "{\"restore\":\"KEEP_REFUND\",\"note\":\"소명 확인\"}", manager).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serviceCards[?(@.service == 'HOTDEAL')].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.history[0].action").value("SERVICE_RESUMED"))
                .andExpect(jsonPath("$.data.history[0].service").value("HOTDEAL"));
        assertThat(line(id, "HOTDEAL")).containsEntry("status", "APPROVED").containsEntry("suspend_code", null).containsEntry("suspended_at", null);
        assertThat(audits(managerId, "BUSINESS_SERVICE_RESUME")).isEqualTo(1);
        assertThat(memberKinds(b[1], "HOTDEAL_RESUMED")).isEqualTo(1);
        call(id, "HOTDEAL", "resume", "{\"restore\":\"EXTEND_RESTORE\"}", manager).andExpect(status().isConflict());
    }

    @Test
    void 신원이_승인이_아니면_서비스_정지는_409() throws Exception {
        long[] b = business();
        // SQL 로 남김: 사업자 상태 칸을 바로 바꾼다 — 전이 메서드는 심사·정지 운영자·사유를 함께 남긴다
        jdbc.update("UPDATE business_profiles SET status = 'PENDING', re_review = true WHERE id = ?", b[0]);
        call(b[0], "AD", "suspend", SUSPEND, manager).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("BUSINESS_STATE_CHANGED"));
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private ResultActions call(long id, String service, String action, String body, Cookie cookie) throws Exception {
        return mvc.perform(withCsrf(post("/api/admin/businesses/" + id + "/services/" + service + "/" + action), cookie).content(body));
    }

    private Map<String, Object> line(long profileId, String service) {
        return jdbc.queryForMap("SELECT status, suspend_code, suspend_text, suspended_at, suspended_by FROM business_services WHERE profile_id = ? AND service = ?", profileId,
                service);
    }

    private int audits(long operatorId, String action) {
        return auditLogs.where().actor(operatorId).action(action).count();
    }

    private int memberKinds(long userId, String kind) {
        return jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'BUSINESS' AND payload->>'kind' = ?", Integer.class, userId, kind);
    }

    /** 승인된 사업자(서비스 두 줄 승인) — {신원 ID, 회원 ID} */
    private long[] business() {
        long user = newMember();
        long id = ids.nextId();
        String number = bizNo();
        // SQL 로 남김: 사업자 신원 줄의 심사 상태·암호문 칸을 심사 기록 없이 바로 세운다 — 팩터리(apply)는 신청(PENDING)만 만든다
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                        + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, reviewed_at, first_approved_at, created_at, updated_at) "
                        + "VALUES (?, ?, 'APPROVED', false, '(주)서비스상사', ?, ?, ?, ?, ?, ?, '06236', ?, 'v1', 'v1', now(), now(), now(), now(), now(), now())",
                id, user, pii.encrypt(BusinessPii.BIZ_NO, number), pii.bizNoHash(number), number.substring(6), pii.encrypt(BusinessPii.OWNER, "홍길동"),
                pii.encrypt(BusinessPii.PHONE, "01012345678"), pii.encrypt(BusinessPii.EMAIL, "s" + user + "@test.local"), pii.encrypt(BusinessPii.ADDRESS1, "서울 강남구 테헤란로 1"));
        for (String service : new String[] {"AD", "HOTDEAL"}) {
            businessFixture.service(ids.nextId(), id, service, "APPROVED");
        }
        return new long[] {id, user};
    }

    /** 체크섬이 맞는 새 등록번호(끝에서 둘째 자리 0) */
    private static String bizNo() {
        int[] w = {1, 3, 7, 1, 3, 7, 1, 3, 5};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append((char) ('0' + ThreadLocalRandom.current().nextInt(i == 0 ? 1 : 0, 10)));
        }
        sb.append('0');
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (sb.charAt(i) - '0') * w[i];
        }
        sum += ((sb.charAt(8) - '0') * 5) / 10;
        return sb.append((char) ('0' + (10 - sum % 10) % 10)).toString();
    }
}
