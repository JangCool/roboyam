package com.community.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.business.service.BusinessPii;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import com.community.permission.entity.PermissionType;
import com.community.permission.entity.PermissionUnit;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 사업자 삭제(business-contract 「사업자 삭제」, 화면 확정 2026-11-02) — 실제 관리자 앱 문맥 + PostgreSQL. {@code DELETE /api/admin/businesses/{id}} {@code { reason }}(5~200자)
 * → 204. 광고·핫딜 신청 건이 한 줄이라도 있으면(취소된 건만 있어도) 409 {@code BUSINESS_HAS_DEALINGS} 이고 아무것도 지우지 않는다. 지우면 신원과 딸린 사업자 표의 줄이 모두
 * 사라지고(연결된 광고주는 연결만 끊긴다) 회원 계정은 그대로, 감사 {@code BUSINESS_DELETE}(상호·끝 4자리·이전 상태·사유 — 평문·암호문 없음). 권한 {@code business.delete}.
 */
class BusinessDeleteHttpTest extends Commerce04AdminTestBase {

    private static final String REASON = "회원 요청으로 사업자 정보 삭제";
    private static final String[] CHILD_TABLES = {"business_services", "business_nts_checks", "business_documents", "business_history", "business_sensitive_views"};

    @Autowired
    private BusinessPii pii;

    private Cookie deleter;
    private Cookie manager;

    @BeforeEach
    void setUp() throws Exception {
        deleter = login(operatorWith(Permission.BUSINESS_READ, Permission.BUSINESS_DELETE));
        manager = login(operatorWith(Permission.BUSINESS_READ, Permission.BUSINESS_MANAGE, Permission.BUSINESS_REVEAL, Permission.BUSINESS_UNSUSPEND));
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

    private record Fixture(long profileId, long userId, String bizNo, long looseDocumentId, long advertiserId) {
    }

    /** 신원 + 딸린 줄 전부(서비스 둘·국세청 스냅샷·붙은 등록증·처리 기록·열람 기록) + 붙지 않은 사본 + 연결된 광고주 */
    private Fixture business(String status, boolean reReview) {
        long user = newMember();
        long id = ids.nextId();
        String number = bizNo();
        // SQL 로 남김: 사업자 신원 줄의 심사 상태·암호문 칸을 심사 기록 없이 바로 세운다 — 팩터리(apply)는 신청(PENDING)만 만든다
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                        + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at, opened_on) "
                        + "VALUES (?, ?, 'PENDING', false, '(주)삭제상사', ?, ?, ?, ?, ?, ?, '06236', ?, 'v1', 'v1', now(), now(), now(), now(), DATE '2021-03-02')",
                id, user, pii.encrypt(BusinessPii.BIZ_NO, number), pii.bizNoHash(number), number.substring(6), pii.encrypt(BusinessPii.OWNER, "홍길동"),
                pii.encrypt(BusinessPii.PHONE, "01012345678"), pii.encrypt(BusinessPii.EMAIL, "b" + user + "@test.local"),
                pii.encrypt(BusinessPii.ADDRESS1, "서울 강남구 테헤란로 1"));
        if ("APPROVED".equals(status)) {
            // SQL 로 남김: 사업자 상태 칸을 바로 바꾼다 — 전이 메서드는 심사·정지 운영자·사유를 함께 남긴다
            jdbc.update("UPDATE business_profiles SET status = 'APPROVED', reviewed_at = now(), first_approved_at = now() WHERE id = ?", id);
        } else if (reReview) { // 재심사 = 승인된 적 있는 심사 중
            jdbc.update("UPDATE business_profiles SET re_review = true, reviewed_at = now(), first_approved_at = now() WHERE id = ?", id);
        }
        for (String service : new String[] {"AD", "HOTDEAL"}) {
            businessFixture.service(ids.nextId(), id, service, "APPROVED".equals(status) ? "APPROVED" : "PENDING");
        }
        jdbc.update("INSERT INTO business_nts_checks (id, profile_id, status, validity, checked_at) VALUES (?, ?, 'CONTINUING', 'MATCH', now())", ids.nextId(), id);
        long cert = ids.nextId();
        // SQL 로 남김: 사업자 상태 칸을 바로 바꾼다 — 전이 메서드는 심사·정지 운영자·사유를 함께 남긴다
        jdbc.update("INSERT INTO business_documents (id, user_id, profile_id, kind, file_name, content_type, size_bytes, content, sha256, created_at, attached_at) "
                + "VALUES (?, ?, ?, 'CERTIFICATE', 'cert.png', 'image/png', 3, ?, ?, now(), now())", cert, user, id, new byte[] {1, 2, 3}, "a".repeat(64));
        jdbc.update("UPDATE business_profiles SET certificate_id = ? WHERE id = ?", cert, id);
        long loose = ids.nextId();
        jdbc.update("INSERT INTO business_documents (id, user_id, kind, file_name, content_type, size_bytes, content, sha256, created_at) "
                + "VALUES (?, ?, 'CERTIFICATE', 'loose.png', 'image/png', 3, ?, ?, now())", loose, user, new byte[] {4, 5, 6}, "b".repeat(64));
        jdbc.update("INSERT INTO business_history (id, profile_id, action, actor_type, actor_id, created_at) VALUES (?, ?, 'APPLIED', 'MEMBER', ?, now())", ids.nextId(), id, user);
        jdbc.update("INSERT INTO business_sensitive_views (id, profile_id, kind, operator_id, created_at) VALUES (?, ?, 'CERTIFICATE', 1, now())", ids.nextId(), id);
        long advertiser = ids.nextId();
        jdbc.update("INSERT INTO ad_advertisers (id, name, business_profile_id, created_by, created_at, updated_at) VALUES (?, ?, ?, 1, now(), now())", advertiser,
                "삭제광고주" + advertiser, id);
        return new Fixture(id, user, number, loose, advertiser);
    }

    /** 신청 건 한 줄(사업자 신청) */
    private void application(Fixture f, String status) {
        long app = ids.nextId();
        jdbc.update("INSERT INTO biz_applications (id, application_no, kind, status, source, business_profile_id, user_id, company_name, biz_no_last4, title, start_at, end_at, "
                        + "preferred_method, quote_supply, quote_vat, quote_total, ad_policy_version, refund_policy_version, agreed_at, stop_fee_rate, created_at, updated_at) "
                        + "VALUES (?, ?, 'AD_BANNER', ?, 'BUSINESS', ?, ?, '(주)삭제상사', ?, '시험 광고', date_trunc('minute', now()) + interval '1 day', "
                        + "date_trunc('minute', now()) + interval '8 day', 'ONLINE', 0, 0, 0, 'v1', 'v1', now(), 0, now(), now())",
                app, "BT-" + (app % 1_000_000_000_000L), status, f.profileId(), f.userId(), f.bizNo().substring(6));
    }

    private ResultActions remove(long id, String body, Cookie who) throws Exception {
        return mvc.perform(withCsrf(delete("/api/admin/businesses/" + id), who).content(body));
    }

    private static String reason(String text) {
        return "{\"reason\":\"" + text + "\"}";
    }

    private int rows(String table, String where, Object arg) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + where, Integer.class, arg);
        return n == null ? 0 : n;
    }

    private int audits(long profileId) {
        return rows("audit_logs", "target_type = 'BUSINESS' AND action = 'BUSINESS_DELETE' AND target_id = ?", Long.toString(profileId));
    }

    @Test
    void 거래가_없으면_어느_상태든_204_로_신원과_딸린_줄을_모두_지우고_회원은_그대로_감사를_남긴다() throws Exception {
        for (Object[] c : new Object[][] {{"PENDING", false}, {"PENDING", true}, {"APPROVED", false}}) {
            Fixture f = business((String) c[0], (Boolean) c[1]);

            remove(f.profileId(), reason("  " + REASON + "  "), deleter).andExpect(status().isNoContent());

            assertThat(rows("business_profiles", "id = ?", f.profileId())).isZero();
            assertThat(rows("business_profiles", "user_id = ?", f.userId())).as("그 회원은 사업자 미신청").isZero();
            for (String table : CHILD_TABLES) {
                assertThat(rows(table, "profile_id = ?", f.profileId())).as(table).isZero();
            }
            assertThat(rows("business_documents", "id = ?", f.looseDocumentId())).as("붙지 않은 사본은 그대로").isEqualTo(1);
            assertThat(rows("users", "id = ? AND status = 'ACTIVE'", f.userId())).as("회원 계정은 그대로").isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT business_profile_id FROM ad_advertisers WHERE id = ?", Long.class, f.advertiserId())).as("광고주는 연결만 끊긴다").isNull();

            assertThat(audits(f.profileId())).isEqualTo(1);
            String detail = auditLogs.where().targetType("BUSINESS").action("BUSINESS_DELETE").target(Long.toString(f.profileId())).single().detail();
            assertThat(detail).contains("(주)삭제상사").contains("bizNoLast4").contains(f.bizNo().substring(6)).contains("\"" + c[0] + "\"").contains("reReview")
                    .contains(REASON).doesNotContain(f.bizNo()).doesNotContain("홍길동").doesNotContain("v1:");

            mvc.perform(withCsrf(get("/api/admin/businesses/" + f.profileId()), manager)).andExpect(status().isNotFound());
        }
    }

    @Test
    void 신청_건이_한_줄이라도_있으면_취소된_건만_있어도_409_BUSINESS_HAS_DEALINGS_이고_아무것도_지우지_않는다() throws Exception {
        for (String appStatus : new String[] {"CANCELED", "SUBMITTED"}) {
            Fixture f = business("APPROVED", false);
            application(f, appStatus);

            remove(f.profileId(), reason(REASON), deleter).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("BUSINESS_HAS_DEALINGS"))
                    .andExpect(jsonPath("$.error.message").isNotEmpty());

            assertThat(rows("business_profiles", "id = ?", f.profileId())).isEqualTo(1);
            for (String table : CHILD_TABLES) {
                assertThat(rows(table, "profile_id = ?", f.profileId())).as(appStatus + " " + table).isPositive();
            }
            assertThat(jdbc.queryForObject("SELECT business_profile_id FROM ad_advertisers WHERE id = ?", Long.class, f.advertiserId())).isEqualTo(f.profileId());
            assertThat(audits(f.profileId())).isZero();
        }
    }

    @Test
    void business_delete_권한이_없으면_403() throws Exception {
        Fixture f = business("PENDING", false);
        remove(f.profileId(), reason(REASON), manager).andExpect(status().isForbidden());
        assertThat(rows("business_profiles", "id = ?", f.profileId())).isEqualTo(1);
    }

    @Test
    void 사유는_5자부터_200자까지_틀리면_400_fields_reason() throws Exception {
        Fixture f = business("PENDING", false);
        for (String body : List.of(reason("네글자다"), reason("가".repeat(201)), reason("    "), "{}")) {
            remove(f.profileId(), body, deleter).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reason").isNotEmpty());
        }
        assertThat(rows("business_profiles", "id = ?", f.profileId())).isEqualTo(1);
        remove(f.profileId(), reason("다섯글자다"), deleter).andExpect(status().isNoContent());
        assertThat(rows("business_profiles", "id = ?", f.profileId())).isZero();
    }

    @Test
    void 없는_신원은_404_BUSINESS_NOT_FOUND() throws Exception {
        remove(ids.nextId(), reason(REASON), deleter).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("BUSINESS_NOT_FOUND"));
    }

    @Test
    void 권한_business_delete_는_사업자_심사의_민감_조치이고_화면_카탈로그와_같다() throws Exception {
        assertThat(Permission.ofCode("business.delete")).contains(Permission.BUSINESS_DELETE);
        assertThat(Permission.BUSINESS_DELETE.type()).isEqualTo(PermissionType.BIZ_REVIEW);
        assertThat(Permission.BUSINESS_DELETE.unit()).isEqualTo(PermissionUnit.SENSITIVE);
        assertThat(Permission.BUSINESS_DELETE.featureReady()).isTrue();
        String line = java.nio.file.Files.readAllLines(java.nio.file.Path.of("..", "..", "apps", "admin-web", "app", "utils", "util-permission-catalog.ts"),
                java.nio.charset.StandardCharsets.UTF_8).stream().filter(l -> l.contains("code: 'business.delete'")).findFirst().orElse(null);
        assertThat(line).isNotNull().contains("unit: 'SENSITIVE'").contains("type: 'bizReview'").contains("featureReady: true");
    }
}
