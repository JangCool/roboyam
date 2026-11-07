package com.community.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.business.service.BusinessPii;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사업자 승인의 국세청 판정(business-contract 「국세청 진위확인 · 공공데이터 설정」 — 승인은 광고·핫딜 돈 흐름의 전제) — 실제 관리자 앱 문맥 + PostgreSQL.
 * 국세청은 local 가짜 구현(끝에서 둘째 자리 규칙). 판정은 <b>잠금이 사유보다 먼저</b>:
 * 진위 불일치 409 {@code BUSINESS_NTS_MISMATCH} → 휴·폐업·없는 번호 409 {@code BUSINESS_NTS_NOT_ACTIVE} → 상태 조회 불가·진위 조회 불가·진위 미확인은 사유(없으면 400
 * {@code fields.ntsOverrideReason}, 있으면 승인 + 감사 {@code BUSINESS_NTS_OVERRIDE}) → 계속 + 일치는 바로. [다시 조회]는 개업일이 있으면 진위확인, 없으면 상태조회만 + {@code UNCHECKED}.
 */
class BusinessNtsApproveHttpTest extends Commerce04AdminTestBase {

    private static final String REASON = "사업자등록증 사본으로 확인함";

    @Autowired
    private BusinessPii pii;

    private Cookie manager;
    private long managerId;

    @BeforeEach
    void setUp() throws Exception {
        managerId = operatorWith(Permission.BUSINESS_READ, Permission.BUSINESS_MANAGE);
        manager = login(managerId);
    }

    /** 체크섬이 맞는 새 등록번호 — 끝에서 둘째 자리가 {@code mark}(가짜 구현 규칙) */
    private static String bizNo(char mark) {
        int[] w = {1, 3, 7, 1, 3, 7, 1, 3, 5};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append((char) ('0' + ThreadLocalRandom.current().nextInt(i == 0 ? 1 : 0, 10)));
        }
        sb.append(mark);
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (sb.charAt(i) - '0') * w[i];
        }
        sum += ((sb.charAt(8) - '0') * 5) / 10;
        return sb.append((char) ('0' + (10 - sum % 10) % 10)).toString();
    }

    /** 심사 중 신원(암호문은 시험 데이터 키로 실제로 잠근다) */
    private long pending(String bizNo, LocalDate openedOn) {
        long user = newMember();
        long id = ids.nextId();
        // SQL 로 남김: 사업자 신원 줄의 심사 상태·암호문 칸을 심사 기록 없이 바로 세운다 — 팩터리(apply)는 신청(PENDING)만 만든다
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, re_review, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, "
                        + "postcode, address1_enc, terms_version, privacy_version, agreed_at, submitted_at, created_at, updated_at, opened_on) "
                        + "VALUES (?, ?, 'PENDING', false, '(주)심사상사', ?, ?, ?, ?, ?, ?, '06236', ?, 'v1', 'v1', now(), now(), now(), now(), ?)",
                id, user, pii.encrypt(BusinessPii.BIZ_NO, bizNo), pii.bizNoHash(bizNo), bizNo.substring(6), pii.encrypt(BusinessPii.OWNER, "홍길동"),
                pii.encrypt(BusinessPii.PHONE, "01012345678"), pii.encrypt(BusinessPii.EMAIL, "b" + user + "@test.local"),
                pii.encrypt(BusinessPii.ADDRESS1, "서울 강남구 테헤란로 1"), openedOn);
        for (String service : new String[] {"AD", "HOTDEAL"}) {
            businessFixture.service(ids.nextId(), id, service, "PENDING");
        }
        return id;
    }

    private long pending(LocalDate openedOn) {
        return pending(bizNo('0'), openedOn);
    }

    /** 가장 최근 국세청 조회 한 줄 */
    private void snapshot(long profileId, String status, String validity) {
        jdbc.update("INSERT INTO business_nts_checks (id, profile_id, status, validity, tax_type, checked_at) VALUES (?, ?, ?, ?, ?, now())", ids.nextId(), profileId, status,
                validity, "CONTINUING".equals(status) ? "부가가치세 일반과세자" : null);
    }

    private String statusOf(long profileId) {
        return jdbc.queryForObject("SELECT status FROM business_profiles WHERE id = ?", String.class, profileId);
    }

    private org.springframework.test.web.servlet.ResultActions approve(long profileId, String reason) throws Exception {
        return mvc.perform(withCsrf(post("/api/admin/businesses/" + profileId + "/approve"), manager)
                .content(reason == null ? "{}" : "{\"ntsOverrideReason\":\"" + reason + "\"}"));
    }

    @Test
    void 진위_불일치는_사유가_있어도_409_BUSINESS_NTS_MISMATCH_휴폐업보다_먼저() throws Exception {
        for (String status : new String[] {"CONTINUING", "UNAVAILABLE", "CLOSED", "NOT_FOUND"}) {
            long id = pending(LocalDate.of(2021, 3, 2));
            snapshot(id, status, "MISMATCH");

            approve(id, REASON).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("BUSINESS_NTS_MISMATCH"));
            assertThat(statusOf(id)).as(status).isEqualTo("PENDING");
        }
    }

    @Test
    void 휴업_폐업_없는_번호는_진위가_일치해도_409_BUSINESS_NTS_NOT_ACTIVE() throws Exception {
        for (String status : new String[] {"PAUSED", "CLOSED", "NOT_FOUND"}) {
            long id = pending(LocalDate.of(2021, 3, 2));
            snapshot(id, status, "MATCH");
            approve(id, REASON).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("BUSINESS_NTS_NOT_ACTIVE"));
        }
    }

    @Test
    void 진위_미확인_UNCHECKED_는_사유가_없으면_400_있으면_승인과_감사() throws Exception {
        long id = pending(null);
        snapshot(id, "CONTINUING", "UNCHECKED");

        approve(id, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.ntsOverrideReason").isNotEmpty());
        approve(id, "짧음").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.ntsOverrideReason").isNotEmpty());
        assertThat(statusOf(id)).isEqualTo("PENDING");

        approve(id, REASON).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.nts.validity").value("UNCHECKED")).andExpect(jsonPath("$.data.openedOn").isEmpty());
        assertThat(statusOf(id)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT nts_override_reason FROM business_profiles WHERE id = ?", String.class, id)).isEqualTo(REASON);
        assertThat(auditLogs.where().targetType("BUSINESS").target(Long.toString(id)).action("BUSINESS_NTS_OVERRIDE").count()).isEqualTo(1);
        assertThat(auditLogs.where().targetType("BUSINESS").target(Long.toString(id)).action("BUSINESS_APPROVE").single().detail()).contains("\"ntsValidity\":\"UNCHECKED\"");
    }

    @Test
    void 진위_조회_불가와_상태_조회_불가는_사유가_없으면_400_있으면_승인() throws Exception {
        String[][] cases = {{"UNAVAILABLE", "UNAVAILABLE"}, {"CONTINUING", "UNAVAILABLE"}, {"UNAVAILABLE", "MATCH"}};
        for (String[] c : cases) {
            long id = pending(LocalDate.of(2021, 3, 2));
            snapshot(id, c[0], c[1]);
            approve(id, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.ntsOverrideReason").isNotEmpty());
            approve(id, REASON).andExpect(status().isOk());
            assertThat(statusOf(id)).as(String.join("/", c)).isEqualTo("APPROVED");
        }
    }

    @Test
    void 계속사업자_진위_일치는_사유_없이_바로_승인() throws Exception {
        long id = pending(LocalDate.of(2021, 3, 2));
        snapshot(id, "CONTINUING", "MATCH");

        approve(id, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.nts.status").value("CONTINUING")).andExpect(jsonPath("$.data.nts.validity").value("MATCH"))
                .andExpect(jsonPath("$.data.openedOn").value("2021-03-02"));
        assertThat(jdbc.queryForObject("SELECT nts_override_reason FROM business_profiles WHERE id = ?", String.class, id)).isNull();
        assertThat(auditLogs.where().targetType("BUSINESS").target(Long.toString(id)).action("BUSINESS_NTS_OVERRIDE").count()).isZero();
    }

    @Test
    void 조회한_적이_없으면_409_BUSINESS_NTS_NOT_CHECKED_상세는_UNAVAILABLE_과_UNCHECKED() throws Exception {
        long id = pending(LocalDate.of(2021, 3, 2));
        mvc.perform(get("/api/admin/businesses/" + id).cookie(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nts.status").value("UNAVAILABLE")).andExpect(jsonPath("$.data.nts.validity").value("UNCHECKED"));
        approve(id, REASON).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("BUSINESS_NTS_NOT_CHECKED"));
    }

    @Test
    void 다시_조회는_개업일이_있으면_진위확인_없으면_상태조회만_UNCHECKED() throws Exception {
        long withDate = pending(LocalDate.of(2021, 3, 2));
        long withoutDate = pending(null);

        mvc.perform(withCsrf(post("/api/admin/businesses/" + withDate + "/nts-check"), manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nts.status").value("CONTINUING")).andExpect(jsonPath("$.data.nts.validity").value("MATCH"));
        mvc.perform(withCsrf(post("/api/admin/businesses/" + withoutDate + "/nts-check"), manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nts.status").value("CONTINUING")).andExpect(jsonPath("$.data.nts.validity").value("UNCHECKED"));

        Map<String, Object> audit = auditLogs.where().targetType("BUSINESS").target(Long.toString(withDate)).action("BUSINESS_NTS_CHECK").lastById().orElseThrow().asMap();
        assertThat(((Number) audit.get("actor_id")).longValue()).isEqualTo(managerId);
        assertThat((String) audit.get("detail")).contains("\"validity\":\"MATCH\"").doesNotContain("홍길동");
        // 진위 미확인은 사유를 적어야 승인된다
        approve(withoutDate, null).andExpect(status().isBadRequest());
        approve(withDate, null).andExpect(status().isOk());
    }

    @Test
    void 다시_조회의_진위_불일치는_상태를_따로_조회해_함께_남기고_승인은_잠긴다() throws Exception {
        long missing = pending(bizNo('6'), LocalDate.of(2021, 3, 2)); // 국세청에 없는 번호 — 진위 02, 상태 NOT_FOUND
        long mismatch = pending(bizNo('5'), LocalDate.of(2021, 3, 2)); // 진위 02, 상태는 계속

        mvc.perform(withCsrf(post("/api/admin/businesses/" + missing + "/nts-check"), manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nts.status").value("NOT_FOUND")).andExpect(jsonPath("$.data.nts.validity").value("MISMATCH"));
        mvc.perform(withCsrf(post("/api/admin/businesses/" + mismatch + "/nts-check"), manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nts.status").value("CONTINUING")).andExpect(jsonPath("$.data.nts.validity").value("MISMATCH"));

        approve(mismatch, REASON).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("BUSINESS_NTS_MISMATCH"));
        List<String> notes = jdbc.queryForList("SELECT note FROM business_history WHERE profile_id = ? AND action = 'NTS_CHECKED'", String.class, mismatch);
        assertThat(notes).containsExactly("MISMATCH");
    }

    @Test
    void 목록_줄에도_진위가_실린다() throws Exception {
        long id = pending(LocalDate.of(2021, 3, 2));
        snapshot(id, "CONTINUING", "MISMATCH");
        String body = mvc.perform(get("/api/admin/businesses").param("status", "PENDING").param("size", "100").cookie(manager)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<Map<String, Object>> items = com.jayway.jsonpath.JsonPath.read(body, "$.data.items[?(@.id == '" + id + "')]");
        if (!items.isEmpty()) { // 심사 대기가 100건을 넘어 다음 쪽에 있으면 건너뛴다(공유 시험 DB)
            assertThat(((Map<?, ?>) items.getFirst().get("nts")).get("validity")).isEqualTo("MISMATCH");
        }
    }
}
