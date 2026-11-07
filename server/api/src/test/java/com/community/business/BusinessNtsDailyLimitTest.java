package com.community.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.business.dto.BusinessApplyRequest;
import com.community.business.dto.MyBusinessView;
import com.community.business.exception.BusinessException;
import com.community.business.service.BusinessNtsDailyLimiter;
import com.community.business.service.MyBusinessService;
import com.community.commerce.CommerceApiTestBase;
import com.community.user.entity.Role;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

/**
 * 사업자 신청·수정의 국세청 진위확인 회원별 하루 횟수 제한(business-contract 「국세청 진위확인 · 공공데이터 설정」, 사용자 결정 2026-11-02) — 불일치는 저장하지 않으므로
 * 같은 회원이 남의 번호에 상호·대표자명·개업일을 바꿔 가며 국세청을 불러 맞혀 보지 못하게 막는다. 실제 공개 사이트 앱 문맥 + PostgreSQL, 국세청은 세는 가짜 구현.
 *
 * <ul>
 *   <li>하루(한국 날짜) 5회까지 — 6회째는 국세청을 부르지 않고 429 {@code BUSINESS_NTS_DAILY_LIMIT} + 다음 한국 0시까지의 {@code retryAfterSeconds}.</li>
 *   <li>국세청을 실제로 부르게 되는 시도만 센다(결과와 무관 — 불일치 400 도 센다). 수정에서 네 칸이 그대로면 세지 않는다. 신청·수정 합산.</li>
 *   <li>한국 날짜가 바뀌면 다시 0. 한도 0 이면 제한 없음(세지도 않는다).</li>
 * </ul>
 */
@Import(BusinessNtsValidateTest.CountingNtsConfig.class)
class BusinessNtsDailyLimitTest extends CommerceApiTestBase {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final String OPENED = "2021-03-02";
    private static final int LIMIT = 5;

    @Autowired
    private MyBusinessService service;
    @Autowired
    private BusinessNtsValidateTest.CountingNts nts;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlatformTransactionManager transactions;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        nts.nextValidate.set(null);
    }

    /** 체크섬이 맞는 새 등록번호 — 끝에서 둘째 자리가 {@code mark}(가짜 구현 규칙: 5 불일치 · 0 계속 + 일치) */
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
        memberFixture.profile(user, "q" + base.substring(Math.max(0, base.length() - 11)));
        return user;
    }

    private String certificate(long user) {
        return service.upload(user, new MockMultipartFile("file", "cert.png", "image/png", PNG)).fileId();
    }

    private BusinessApplyRequest request(long user, String bizNo, String company, String owner, String openedOn) {
        return new BusinessApplyRequest(company, bizNo, owner, "01012345678", "biz" + user + "@test.local", new MyBusinessView.Address("06236", "서울 강남구 테헤란로 1", "2층"),
                certificate(user), "v1", "v1", openedOn);
    }

    /** 수정 요청 — 번호·연락처는 비워 두면 그대로 */
    private BusinessApplyRequest edit(long user, String company, String owner, String openedOn) {
        return new BusinessApplyRequest(company, null, owner, null, "biz" + user + "@test.local", new MyBusinessView.Address("06236", "서울 강남구 테헤란로 1", ""),
                certificate(user), "v1", "v1", openedOn);
    }

    /** 불일치 신청 한 번(400 — 신청을 받지 않지만 센다) */
    private void mismatchApply(long user) {
        BusinessApplyRequest body = request(user, bizNo('5'), "(주)맞혀보기상사", "홍길동", OPENED);
        assertThatThrownBy(() -> service.apply(user, body)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo("BUSINESS_NTS_MISMATCH"));
    }

    private void assertDailyLimit(ThrowingRunnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(429);
            assertThat(e.getErrorCode()).isEqualTo("BUSINESS_NTS_DAILY_LIMIT");
            assertThat(e.getRetryAfterSeconds()).isPositive();
        });
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private int counted(long user, LocalDate day) {
        Integer n = jdbc.queryForObject("SELECT coalesce(max(count), 0) FROM business_nts_daily_counts WHERE user_id = ? AND day = ?", Integer.class, user, day);
        return n == null ? 0 : n;
    }

    private String applyJson(long user, String bizNo) {
        return "{\"companyName\":\"(주)맞혀보기상사\",\"bizNo\":\"" + bizNo + "\",\"ownerName\":\"홍길동\",\"openedOn\":\"" + OPENED + "\",\"phone\":\"01012345678\","
                + "\"email\":\"biz" + user + "@test.local\",\"address\":{\"postcode\":\"06236\",\"address1\":\"서울 강남구 테헤란로 1\",\"address2\":\"\"},"
                + "\"certificateFileId\":\"" + certificate(user) + "\",\"agreedTermsVersion\":\"v1\",\"agreedPrivacyVersion\":\"v1\"}";
    }

    // ── 하루 5회 ─────────────────────────────────────────────────────────────

    @Test
    void 불일치_신청도_세고_5회까지는_국세청을_부르며_6회째는_부르지_않고_429_와_다음_한국_0시까지의_retryAfter() throws Exception {
        // 한국 시각 오늘 23:59:30 — 다음 0시까지 30초
        LocalDate today = ServiceTime.today(clock);
        clock.set(today.atTime(LocalTime.of(23, 59, 30)).atZone(ServiceTime.ZONE).toInstant());
        long user = member();
        int validates = nts.validates.get();
        for (int i = 0; i < LIMIT; i++) {
            mismatchApply(user);
        }
        assertThat(nts.validates.get() - validates).isEqualTo(LIMIT);
        assertThat(counted(user, today)).as("업무가 400 으로 끝나도 시도 수는 남는다").isEqualTo(LIMIT);

        AuthUser principal = new AuthUser(user, Role.USER);
        mvc.perform(post("/api/business/me").principal(new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()))
                        .contentType(MediaType.APPLICATION_JSON).content(applyJson(user, bizNo('0'))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_NTS_DAILY_LIMIT"))
                .andExpect(jsonPath("$.error.retryAfterSeconds").value(30))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(header().string("Retry-After", "30"));

        assertThat(nts.validates.get() - validates).as("6회째는 국세청을 부르지 않는다").isEqualTo(LIMIT);
        assertThat(counted(user, today)).as("한도에 닿은 줄은 더 올리지 않는다").isEqualTo(LIMIT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_profiles WHERE user_id = ?", Integer.class, user)).isZero();
    }

    @Test
    void 이미_신청함_같은_다른_검사가_한도보다_먼저다() {
        long user = member();
        service.apply(user, request(user, bizNo('0'), "(주)먼저상사", "홍길동", OPENED)); // 1회
        for (int i = 0; i < LIMIT - 1; i++) {
            jdbc.update("UPDATE business_nts_daily_counts SET count = count + 1 WHERE user_id = ?", user); // 오늘 한도를 다 쓴 것으로
        }
        BusinessApplyRequest again = request(user, bizNo('0'), "(주)먼저상사", "홍길동", OPENED);
        assertThatThrownBy(() -> service.apply(user, again)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo("BUSINESS_ALREADY_APPLIED"));
    }

    // ── 수정·합산 ────────────────────────────────────────────────────────────

    @Test
    void 수정에서_네_칸이_그대로면_세지_않고_한도를_다_써도_고칠_수_있다() {
        long user = member();
        service.apply(user, request(user, bizNo('0'), "(주)그대로상사", "홍길동", OPENED)); // 1회
        // SQL 로 남김: 사업자 상태 칸을 바로 바꾼다 — 전이 메서드는 심사·정지 운영자·사유를 함께 남긴다
        jdbc.update("UPDATE business_profiles SET status = 'APPROVED', reviewed_at = now(), first_approved_at = now() WHERE user_id = ?", user);
        LocalDate today = ServiceTime.today(clock);
        int validates = nts.validates.get();

        for (int i = 0; i < LIMIT + 2; i++) {
            service.update(user, edit(user, "(주)그대로상사", "홍길동", OPENED));
        }
        assertThat(nts.validates.get()).isEqualTo(validates);
        assertThat(counted(user, today)).isEqualTo(1);

        jdbc.update("UPDATE business_nts_daily_counts SET count = ? WHERE user_id = ? AND day = ?", LIMIT, user, today); // 오늘 한도를 다 쓴 것으로
        MyBusinessView.Profile profile = service.update(user, edit(user, "(주)그대로상사", "홍길동", OPENED));
        assertThat(profile.status()).as("국세청을 부르지 않는 수정은 한도와 무관").isEqualTo("APPROVED");
    }

    @Test
    void 신청과_수정을_합쳐_하루_5회() {
        long user = member();
        LocalDate today = ServiceTime.today(clock);
        mismatchApply(user);                                                     // 1 신청(불일치)
        mismatchApply(user);                                                     // 2 신청(불일치)
        service.apply(user, request(user, bizNo('0'), "(주)합산상사", "홍길동", OPENED)); // 3 신청(일치)
        // SQL 로 남김: 사업자 상태 칸을 바로 바꾼다 — 전이 메서드는 심사·정지 운영자·사유를 함께 남긴다
        jdbc.update("UPDATE business_profiles SET status = 'APPROVED', reviewed_at = now(), first_approved_at = now() WHERE user_id = ?", user);
        service.update(user, edit(user, "(주)합산상사", "김철수", OPENED));             // 4 수정(대표자명)
        service.update(user, edit(user, "(주)합산상사", "김철수", "2021-03-03"));       // 5 수정(개업일)
        assertThat(counted(user, today)).isEqualTo(LIMIT);
        int validates = nts.validates.get();

        assertDailyLimit(() -> service.update(user, edit(user, "(주)합산상사", "이영희", "2021-03-03")));
        assertThat(nts.validates.get()).isEqualTo(validates);
        assertThat(jdbc.queryForObject("SELECT opened_on FROM business_profiles WHERE user_id = ?", LocalDate.class, user)).isEqualTo(LocalDate.parse("2021-03-03"));
        assertThat(service.profile(user).ownerName()).as("한도에 걸린 수정은 아무것도 바꾸지 않는다").isEqualTo("김철수");
    }

    // ── 날짜·설정 ────────────────────────────────────────────────────────────

    @Test
    void 한국_날짜가_바뀌면_다시_0() {
        LocalDate today = ServiceTime.today(clock);
        clock.set(today.atTime(LocalTime.of(23, 50)).atZone(ServiceTime.ZONE).toInstant());
        long user = member();
        for (int i = 0; i < LIMIT; i++) {
            mismatchApply(user);
        }
        assertDailyLimit(() -> service.apply(user, request(user, bizNo('0'), "(주)내일상사", "홍길동", OPENED)));

        clock.set(today.plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant()); // 한국 0시
        int validates = nts.validates.get();
        MyBusinessView.Profile profile = service.apply(user, request(user, bizNo('0'), "(주)내일상사", "홍길동", OPENED));

        assertThat(profile.status()).isEqualTo("PENDING");
        assertThat(nts.validates.get() - validates).isEqualTo(1);
        assertThat(counted(user, today.plusDays(1))).isEqualTo(1);
        assertThat(counted(user, today)).as("어제 줄은 그대로(정리 배치가 지운다)").isEqualTo(LIMIT);
    }

    @Test
    void 한도_0_이면_제한_없고_세지도_않는다() {
        long user = member();
        BusinessNtsDailyLimiter unlimited = new BusinessNtsDailyLimiter(dataSource, transactions, 0, clock);
        for (int i = 0; i < 20; i++) {
            assertThat(unlimited.acquire(user)).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_nts_daily_counts WHERE user_id = ?", Integer.class, user)).isZero();

        BusinessNtsDailyLimiter two = new BusinessNtsDailyLimiter(dataSource, transactions, 2, clock);
        assertThat(two.acquire(user)).isEqualTo(1);
        assertThat(two.acquire(user)).isEqualTo(2);
        assertDailyLimit(() -> two.acquire(user));
        assertThat(counted(user, ServiceTime.today(clock))).isEqualTo(2);
    }

    @Test
    void 기본_한도는_5() {
        assertThat(context.getBean(com.community.business.nts.NtsProperties.class).dailyValidateLimit()).isEqualTo(LIMIT);
    }

    @Test
    void 정리_배치는_오늘_어제_줄을_남기고_2일_지난_줄만_지운다() {
        long user = member();
        LocalDate today = ServiceTime.dateOf(Instant.now());
        for (int ago : new int[] {0, 1, 2, 10}) {
            jdbc.update("INSERT INTO business_nts_daily_counts (user_id, day, count) VALUES (?, ?, 1)", user, today.minusDays(ago));
        }
        jdbc.execute("CALL batch_business_nts_count_cleanup('MANUAL', NULL, NULL)");

        assertThat(jdbc.queryForList("SELECT day FROM business_nts_daily_counts WHERE user_id = ? ORDER BY day DESC", LocalDate.class, user))
                .containsExactly(today, today.minusDays(1));
        assertThat(jdbc.queryForObject("SELECT message_key FROM batch_runs WHERE job_key = 'business-nts-count-cleanup' ORDER BY started_at DESC, id DESC LIMIT 1",
                String.class)).isEqualTo("batch.result.businessNtsCountCleanup");
        assertThat(com.community.batch.core.BatchCatalog.ofKey("business-nts-count-cleanup")).isEqualTo(com.community.batch.core.BatchCatalog.BUSINESS_NTS_COUNT_CLEANUP);
    }
}
