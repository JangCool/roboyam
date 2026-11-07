package com.community.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.business.dto.BusinessApplyRequest;
import com.community.business.dto.MyBusinessView;
import com.community.business.entity.NtsValidity;
import com.community.business.exception.BusinessException;
import com.community.business.nts.FakeNtsStatusClient;
import com.community.business.nts.NtsLookup;
import com.community.business.nts.NtsProviderKind;
import com.community.business.nts.NtsStatusClient;
import com.community.business.service.MyBusinessService;
import com.community.commerce.CommerceApiTestBase;
import com.community.user.entity.Role;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.time.ServiceTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 사업자 신청·수정의 국세청 진위확인(business-contract 「국세청 진위확인 · 공공데이터 설정」) — 사업자 승인은 광고·핫딜 돈 흐름의 전제라 규칙을 시험으로 막는다.
 * 실제 공개 사이트 앱 문맥 + PostgreSQL. 국세청은 local 가짜 구현(끝에서 둘째 자리 규칙)을 감싼 세는 구현 — 부른 횟수를 세고, 한 번만 결과를 바꿀 수 있다.
 *
 * <ul>
 *   <li>불일치 → 400 {@code BUSINESS_NTS_MISMATCH} + {@code fields.companyName·ownerName·openedOn}, 신청을 받지 않는다(신원 줄·스냅샷 없음).</li>
 *   <li>조회 불가 → 신청은 받고 스냅샷 {@code UNAVAILABLE}. 일치 → 같은 응답의 상태로 스냅샷(상태조회는 따로 부르지 않는다). 휴업이어도 신청은 받는다.</li>
 *   <li>개업일 필수·형식·오늘 이후 불가. 수정은 번호·상호·대표자명·개업일 중 하나라도 바뀔 때만 다시 확인하고, 개업일이 없던 옛 사업자는 개업일 필수.</li>
 * </ul>
 */
@Import(BusinessNtsValidateTest.CountingNtsConfig.class)
class BusinessNtsValidateTest extends CommerceApiTestBase {

    /** 가짜 구현을 감싸 부른 횟수를 센다 — {@link #nextValidate} 가 있으면 다음 진위확인 한 번만 그 결과 */
    static class CountingNts implements NtsStatusClient {

        final FakeNtsStatusClient fake = new FakeNtsStatusClient();
        final AtomicInteger lookups = new AtomicInteger();
        final AtomicInteger validates = new AtomicInteger();
        final AtomicReference<NtsLookup> nextValidate = new AtomicReference<>();

        @Override
        public NtsProviderKind kind() {
            return NtsProviderKind.FAKE;
        }

        @Override
        public NtsLookup lookup(String bizNo) {
            lookups.incrementAndGet();
            return fake.lookup(bizNo);
        }

        @Override
        public NtsLookup validate(String bizNo, LocalDate openedOn, String ownerName, String companyName) {
            validates.incrementAndGet();
            NtsLookup forced = nextValidate.getAndSet(null);
            return forced != null ? forced : fake.validate(bizNo, openedOn, ownerName, companyName);
        }
    }

    @TestConfiguration
    static class CountingNtsConfig {

        @Bean
        @Primary
        CountingNts countingNts() {
            return new CountingNts();
        }
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final String OPENED = "2021-03-02";

    @Autowired
    private MyBusinessService service;
    @Autowired
    private CountingNts nts;
    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        nts.nextValidate.set(null);
    }

    /** 체크섬이 맞는 새 등록번호 — 끝에서 둘째 자리가 {@code mark}(가짜 구현 규칙), 앞 8자리는 무작위(같은 시험 DB 의 살아 있는 번호와 겹치지 않게) */
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

    private long member() {
        long user = newMember();
        String base = Long.toString(user, 36);
        memberFixture.profile(user, "v" + base.substring(Math.max(0, base.length() - 11)));
        return user;
    }

    private String certificate(long user) {
        return service.upload(user, new MockMultipartFile("file", "cert.png", "image/png", PNG)).fileId();
    }

    private BusinessApplyRequest request(long user, String bizNo, String company, String owner, String openedOn) {
        return new BusinessApplyRequest(company, bizNo, owner, "01012345678", "biz" + user + "@test.local", new MyBusinessView.Address("06236", "서울 강남구 테헤란로 1", "2층"),
                certificate(user), "v1", "v1", openedOn);
    }

    private Map<String, Object> latestNts(long user) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT c.status, c.validity, c.tax_type, c.message, c.checked_by FROM business_nts_checks c "
                + "JOIN business_profiles p ON p.id = c.profile_id WHERE p.user_id = ? ORDER BY c.checked_at DESC, c.id DESC LIMIT 1", user);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private int ntsRows(long user) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM business_nts_checks c JOIN business_profiles p ON p.id = c.profile_id WHERE p.user_id = ?", Integer.class, user);
        return n == null ? 0 : n;
    }

    private int profiles(long user) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM business_profiles WHERE user_id = ?", Integer.class, user);
        return n == null ? 0 : n;
    }

    /** 승인된 사업자(신청 → SQL 로 승인) */
    private long approved(String bizNo, String company, String owner) {
        long user = member();
        service.apply(user, request(user, bizNo, company, owner, OPENED));
        // SQL 로 남김: 사업자 상태 칸을 바로 바꾼다 — 전이 메서드는 심사·정지 운영자·사유를 함께 남긴다
        jdbc.update("UPDATE business_profiles SET status = 'APPROVED', reviewed_at = now(), first_approved_at = now() WHERE user_id = ?", user);
        return user;
    }

    // ── 신청 ───────────────────────────────────────────────────────────────

    @Test
    void 진위_불일치면_400_BUSINESS_NTS_MISMATCH_와_상호_대표자명_개업일_세_칸이고_신청을_받지_않는다() {
        for (char mark : new char[] {'5', '6'}) { // 5 불일치 · 6 국세청에 없는 번호(진위도 02)
            long user = member();
            BusinessApplyRequest body = request(user, bizNo(mark), "(주)불일치상사", "홍길동", OPENED);

            assertThatThrownBy(() -> service.apply(user, body)).isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getStatus().value()).isEqualTo(400);
                assertThat(e.getErrorCode()).isEqualTo("BUSINESS_NTS_MISMATCH");
                assertThat(e.getFields()).containsOnlyKeys("companyName", "ownerName", "openedOn");
            });
            assertThat(profiles(user)).as("신원 줄을 만들지 않는다").isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM business_documents WHERE user_id = ? AND profile_id IS NOT NULL", Integer.class, user))
                    .as("사본도 붙이지 않는다").isZero();
        }
    }

    @Test
    void 불일치_응답_모양은_400_code_와_fields_세_칸() throws Exception {
        long user = member();
        AuthUser principal = new AuthUser(user, Role.USER);
        String body = "{\"companyName\":\"(주)불일치상사\",\"bizNo\":\"" + bizNo('5') + "\",\"ownerName\":\"홍길동\",\"openedOn\":\"" + OPENED + "\",\"phone\":\"01012345678\","
                + "\"email\":\"biz" + user + "@test.local\",\"address\":{\"postcode\":\"06236\",\"address1\":\"서울 강남구 테헤란로 1\",\"address2\":\"\"},"
                + "\"certificateFileId\":\"" + certificate(user) + "\",\"agreedTermsVersion\":\"v1\",\"agreedPrivacyVersion\":\"v1\"}";

        mvc.perform(post("/api/business/me").principal(new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_NTS_MISMATCH"))
                .andExpect(jsonPath("$.error.fields.companyName").isNotEmpty())
                .andExpect(jsonPath("$.error.fields.ownerName").isNotEmpty())
                .andExpect(jsonPath("$.error.fields.openedOn").isNotEmpty())
                .andExpect(jsonPath("$.error.fields.bizNo").doesNotExist());
    }

    @Test
    void 조회_불가면_신청은_받고_스냅샷은_UNAVAILABLE() {
        long user = member();
        MyBusinessView.Profile profile = service.apply(user, request(user, bizNo('9'), "(주)장애상사", "홍길동", OPENED));

        assertThat(profile.status()).isEqualTo("PENDING");
        assertThat(profile.openedOn()).isEqualTo(LocalDate.parse(OPENED));
        Map<String, Object> check = latestNts(user);
        assertThat(check.get("status")).isEqualTo("UNAVAILABLE");
        assertThat(check.get("validity")).isEqualTo("UNAVAILABLE");
        assertThat(check.get("message")).isEqualTo("FAKE_DOWN");
        assertThat(check.get("checked_by")).as("서버가 한 조회").isNull();
    }

    @Test
    void 일치면_같은_응답의_상태로_스냅샷을_채우고_상태조회를_따로_부르지_않는다() {
        long user = member();
        int lookups = nts.lookups.get();
        int validates = nts.validates.get();

        MyBusinessView.Profile profile = service.apply(user, request(user, bizNo('0'), "(주)일치상사", "홍길동", OPENED));

        assertThat(profile.status()).isEqualTo("PENDING");
        assertThat(nts.validates.get() - validates).isEqualTo(1);
        assertThat(nts.lookups.get() - lookups).as("진위확인이 상태조회를 대신한다").isZero();
        assertThat(ntsRows(user)).isEqualTo(1);
        Map<String, Object> check = latestNts(user);
        assertThat(check.get("status")).isEqualTo("CONTINUING");
        assertThat(check.get("validity")).isEqualTo(NtsValidity.MATCH.name());
        assertThat(check.get("tax_type")).isEqualTo("부가가치세 일반과세자");
        assertThat(jdbc.queryForObject("SELECT opened_on FROM business_profiles WHERE user_id = ?", LocalDate.class, user)).isEqualTo(LocalDate.parse(OPENED));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_history h JOIN business_profiles p ON p.id = h.profile_id WHERE p.user_id = ? AND h.action = 'NTS_CHECKED' "
                + "AND h.actor_type = 'SYSTEM'", Integer.class, user)).isEqualTo(1);
    }

    @Test
    void 진위_일치여도_휴업이면_신청은_받고_상태는_휴업() {
        long user = member();
        service.apply(user, request(user, bizNo('7'), "(주)휴업상사", "홍길동", OPENED));

        Map<String, Object> check = latestNts(user);
        assertThat(check.get("status")).isEqualTo("PAUSED");
        assertThat(check.get("validity")).isEqualTo("MATCH");
    }

    @Test
    void 개업일은_필수_YYYY_MM_DD_오늘_이후_불가_국세청을_부르기_전에_막는다() {
        long user = member();
        int validates = nts.validates.get();
        String tomorrow = ServiceTime.today(clock).plusDays(1).toString();
        for (String bad : new String[] {null, "", "2021-3-2", "2021/03/02", "2021-02-30", tomorrow, "1899-12-31"}) {
            BusinessApplyRequest body = request(user, bizNo('0'), "(주)개업일상사", "홍길동", bad);
            assertThatThrownBy(() -> service.apply(user, body)).as(String.valueOf(bad)).isInstanceOfSatisfying(ValidationException.class,
                    e -> assertThat(e.getFields()).containsKey("openedOn"));
        }
        assertThat(nts.validates.get()).isEqualTo(validates);
        assertThat(profiles(user)).isZero();
        // 오늘은 된다
        service.apply(user, request(user, bizNo('0'), "(주)개업일상사", "홍길동", ServiceTime.today(clock).toString()));
        assertThat(profiles(user)).isEqualTo(1);
    }

    @Test
    void 반려_뒤_재신청도_진위확인을_다시_한다_번호를_비워_두면_저장된_번호로() {
        String number = bizNo('0');
        long user = member();
        service.apply(user, request(user, number, "(주)재신청상사", "홍길동", OPENED));
        // SQL 로 남김: 사업자 상태 칸을 바로 바꾼다 — 전이 메서드는 심사·정지 운영자·사유를 함께 남긴다
        jdbc.update("UPDATE business_profiles SET status = 'REJECTED', reject_code = 'OTHER', reject_text = '시험', reviewed_at = now() WHERE user_id = ?", user);
        int validates = nts.validates.get();
        nts.nextValidate.set(NtsLookup.mismatch());

        BusinessApplyRequest again = request(user, null, "(주)재신청상사", "홍길동", "2020-01-01");
        assertThatThrownBy(() -> service.apply(user, again)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo("BUSINESS_NTS_MISMATCH"));
        assertThat(nts.validates.get() - validates).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM business_profiles WHERE user_id = ?", String.class, user)).isEqualTo("REJECTED");

        service.apply(user, request(user, null, "(주)재신청상사", "홍길동", "2020-01-01"));
        assertThat(jdbc.queryForObject("SELECT status FROM business_profiles WHERE user_id = ?", String.class, user)).isEqualTo("PENDING");
        assertThat(latestNts(user).get("validity")).isEqualTo("MATCH");
    }

    // ── 수정 ───────────────────────────────────────────────────────────────

    @Test
    void 수정은_네_칸이_그대로면_진위확인을_다시_부르지_않는다() {
        long user = approved(bizNo('0'), "(주)그대로상사", "홍길동");
        int validates = nts.validates.get();
        int rows = ntsRows(user);

        BusinessApplyRequest body = new BusinessApplyRequest("(주)그대로상사", null, "홍길동", null, "new" + user + "@test.local",
                new MyBusinessView.Address("06236", "서울 강남구 테헤란로 2", ""), certificate(user), "v1", "v1", OPENED);
        MyBusinessView.Profile profile = service.update(user, body);

        assertThat(profile.status()).isEqualTo("APPROVED");
        assertThat(profile.reReview()).isFalse();
        assertThat(nts.validates.get()).isEqualTo(validates);
        assertThat(ntsRows(user)).isEqualTo(rows);
    }

    @Test
    void 수정에서_대표자명_개업일_상호_번호_중_하나라도_바뀌면_다시_진위확인() {
        String number = bizNo('0');
        long user = approved(number, "(주)바뀜상사", "홍길동");
        String[][] changes = {
                {null, "(주)바뀜상사", "김철수", OPENED},      // 대표자명
                {null, "(주)바뀜상사", "김철수", "2021-03-03"}, // 개업일
                {null, "주식회사 바뀜상사", "김철수", "2021-03-03"}, // 상호(재심사)
                {bizNo('0'), "주식회사 바뀜상사", "김철수", "2021-03-03"}, // 번호(재심사)
        };
        for (String[] c : changes) {
            int validates = nts.validates.get();
            int rows = ntsRows(user);
            service.update(user, new BusinessApplyRequest(c[1], c[0], c[2], null, "biz" + user + "@test.local",
                    new MyBusinessView.Address("06236", "서울 강남구 테헤란로 1", ""), certificate(user), "v1", "v1", c[3]));
            assertThat(nts.validates.get() - validates).as(String.join("|", java.util.Arrays.stream(c).map(String::valueOf).toList())).isEqualTo(1);
            assertThat(ntsRows(user)).isEqualTo(rows + 1);
        }
        assertThat(jdbc.queryForObject("SELECT re_review FROM business_profiles WHERE user_id = ?", Boolean.class, user)).as("상호·번호를 바꿔 재심사").isTrue();
    }

    @Test
    void 수정에서_진위_불일치면_400_이고_아무것도_바뀌지_않는다() {
        long user = approved(bizNo('0'), "(주)안바뀜상사", "홍길동");
        nts.nextValidate.set(NtsLookup.mismatch());

        BusinessApplyRequest body = new BusinessApplyRequest("(주)다른이름상사", null, "홍길동", null, "biz" + user + "@test.local",
                new MyBusinessView.Address("06236", "서울 강남구 테헤란로 1", ""), certificate(user), "v1", "v1", OPENED);
        assertThatThrownBy(() -> service.update(user, body)).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(400);
            assertThat(e.getErrorCode()).isEqualTo("BUSINESS_NTS_MISMATCH");
        });
        Map<String, Object> row = jdbc.queryForMap("SELECT company_name, status, re_review FROM business_profiles WHERE user_id = ?", user);
        assertThat(row.get("company_name")).isEqualTo("(주)안바뀜상사");
        assertThat(row.get("status")).isEqualTo("APPROVED");
        assertThat(row.get("re_review")).isEqualTo(false);
    }

    @Test
    void 개업일이_없던_옛_승인_사업자는_고칠_때_개업일이_필수이고_넣으면_진위확인을_한다() {
        long user = approved(bizNo('0'), "(주)옛상사", "홍길동");
        // SQL 로 남김: 사업자 상태 칸을 바로 바꾼다 — 전이 메서드는 심사·정지 운영자·사유를 함께 남긴다
        jdbc.update("UPDATE business_profiles SET opened_on = NULL WHERE user_id = ?", user);
        assertThat(service.profile(user).openedOn()).isNull();

        BusinessApplyRequest missing = new BusinessApplyRequest("(주)옛상사", null, "홍길동", null, "biz" + user + "@test.local",
                new MyBusinessView.Address("06236", "서울 강남구 테헤란로 1", ""), certificate(user), "v1", "v1", null);
        assertThatThrownBy(() -> service.update(user, missing)).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.getFields()).containsKey("openedOn"));

        int validates = nts.validates.get();
        MyBusinessView.Profile profile = service.update(user, new BusinessApplyRequest("(주)옛상사", null, "홍길동", null, "biz" + user + "@test.local",
                new MyBusinessView.Address("06236", "서울 강남구 테헤란로 1", ""), certificate(user), "v1", "v1", OPENED));
        assertThat(nts.validates.get() - validates).isEqualTo(1);
        assertThat(profile.openedOn()).isEqualTo(LocalDate.parse(OPENED));
        assertThat(profile.status()).isEqualTo("APPROVED");
        assertThat(latestNts(user).get("validity")).isEqualTo("MATCH");
    }

    @Test
    void 이미_심사_중이면_국세청을_부르지_않고_409() {
        long user = member();
        service.apply(user, request(user, bizNo('0'), "(주)중복상사", "홍길동", OPENED));
        int validates = nts.validates.get();

        BusinessApplyRequest again = request(user, bizNo('0'), "(주)중복상사", "홍길동", OPENED);
        assertThatThrownBy(() -> service.apply(user, again)).isInstanceOfSatisfying(CommonException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo("BUSINESS_ALREADY_APPLIED"));
        assertThat(nts.validates.get()).isEqualTo(validates);
    }
}
