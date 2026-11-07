package com.community.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.service.AuditLogService;
import com.community.business.nts.NtsProperties;
import com.community.business.nts.NtsProviderKind;
import com.community.business.nts.OdcloudNtsApi;
import com.community.holiday.batch.HolidaySyncJob;
import com.community.holiday.client.SpecialDayApi;
import com.community.holiday.entity.HolidaySource;
import com.community.holiday.repository.master.SiteHolidayStore;
import com.community.holiday.service.HolidaySyncService;
import com.community.holiday.service.SiteHolidayService;
import com.community.publicdata.dto.response.PublicDataSettingsView;
import com.community.publicdata.repository.master.PublicDataEndpointStore;
import com.community.publicdata.repository.master.PublicDataSettingStore;
import com.community.publicdata.service.PublicDataEndpointService;
import com.community.publicdata.service.PublicDataSettingsService;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.JpaTestContext;
import com.community.testsupport.MutableTestClock;
import com.community.zconfiguration.crypto.SecretEncryptor;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 공공데이터 공휴일 받기의 <b>맞추기 규칙</b>(사용자 지시 2026-11-05 — {@link HolidaySyncService})을 실제 PostgreSQL(쉬는 날 표 V150)과 HTTP 흉내로 막는다. 실제 data.go.kr 은 부르지 않는다.
 * 시계는 2040-03-01 이라 받는 해는 2040·2041 — 다른 시험이 쓰는 기본값(2026·2027)과 겹치지 않고, 끝나면 두 해의 줄을 지운다.
 *
 * <p>막는 규칙: 새 날 넣기 · 사라진 날 지우기 · 이름(대체공휴일 여부) 바꾸기 · 그 해 SEED 는 첫 성공 때 지움 · OPERATOR 는 절대 안 건드림 · 빈 해는 그대로(SEED 도 남음) ·
 * 한 해 실패면 그 해만 그대로 · 모두 실패면 FAILED · 키 없으면 부르지 않고 NO_KEY · 결과 기록 · 운영자 받기 감사 · 바꾼 주소로 부름 · 연결 시험(올해 1월) · 배치 결과(건너뜀·실패 예외).
 */
class HolidaySyncServiceTest {

    private static final String KEY = "holiday-test-key";
    private static final int Y1 = 2040;
    private static final int Y2 = 2041;

    private DataSource ds;
    private JdbcTemplate jdbc;
    private SiteHolidayStore store;
    private MockRestServiceServer server;
    private SpecialDayApi api;
    private ObjectProvider<Clock> clocks;
    private final List<AuditEvent> audits = new ArrayList<>();
    private AuditLogService audit;
    private PublicDataEndpointService endpoints;
    /** 해·달 → 응답 본문(없으면 빈 items) · 해 → 그 해 모든 달 HTTP 503 */
    private final Map<String, String> bodies = new HashMap<>();
    private final List<Integer> failingYears = new ArrayList<>();
    private final List<URI> requested = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        ds = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(ds);
        cleanup();
        jdbc.update("DELETE FROM public_data_endpoints");
        store = JpaTestContext.create(SiteHolidayStore.class);
        clocks = new StaticListableBeanFactory(Map.of("clock", new MutableTestClock(Instant.parse("2040-03-01T03:00:00Z")))).getBeanProvider(Clock.class);
        audit = new AuditLogService(null, null) {
            @Override
            public void record(AuditEvent event) {
                audits.add(event);
            }
        };
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        server.expect(manyTimes(), requestTo(org.hamcrest.Matchers.startsWith("https://"))).andRespond(request -> {
            calls.incrementAndGet();
            requested.add(request.getURI());
            Map<String, List<String>> q = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            String year = first(q, "solYear", "y");
            String month = first(q, "solMonth", "m");
            if (failingYears.contains(Integer.parseInt(year))) {
                return new MockClientHttpResponse(new byte[0], HttpStatus.SERVICE_UNAVAILABLE);
            }
            String items = bodies.getOrDefault(year + "-" + month, "\"\"");
            MockClientHttpResponse response = new MockClientHttpResponse(("{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE.\"},"
                    + "\"body\":{\"items\":" + items + "}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8), HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response;
        });
        api = new SpecialDayApi(builder.build());
        endpoints = JpaTestContext.transactional(new PublicDataEndpointService(JpaTestContext.create(PublicDataEndpointStore.class), nts(KEY), audit, clocks));
    }

    @AfterEach
    void tearDown() {
        cleanup();
        jdbc.update("DELETE FROM public_data_endpoints");
    }

    private void cleanup() {
        jdbc.update("DELETE FROM site_holidays WHERE holiday_date BETWEEN ? AND ?", LocalDate.of(Y1, 1, 1), LocalDate.of(Y2, 12, 31));
    }

    /** 시험 운영자(이 시험이 만든 줄) */
    private long operator() {
        long id = 9_400_000_000L + System.nanoTime() % 1_000_000;
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, now(), now())", id,
                "holiday-" + id + "@test.local", "hol" + (id % 1_000_000_000L));
        return id;
    }

    private static String first(Map<String, List<String>> q, String a, String b) {
        List<String> v = q.containsKey(a) ? q.get(a) : q.get(b);
        return v.getFirst();
    }

    private static NtsProperties nts(String key) {
        return new NtsProperties(NtsProviderKind.FAKE, key, "https://api.odcloud.kr", 5);
    }

    private HolidaySyncService service(String key) {
        PublicDataSettingsService keys = JpaTestContext.transactional(new PublicDataSettingsService(JpaTestContext.create(PublicDataSettingStore.class), new SecretEncryptor(""), nts(key),
                new OdcloudNtsApi(RestClient.create()), audit, clocks, endpoints));
        return new HolidaySyncService(api, store, keys, endpoints, audit, JpaTestContext.transactions(), clocks);
    }

    /** 그 달 응답 줄들 */
    private void month(int year, int month, String... items) {
        bodies.put(year + "-" + "%02d".formatted(month), "{\"item\":[" + String.join(",", items) + "]}");
    }

    private static String item(String name, String ymd) {
        return "{\"dateKind\":\"01\",\"dateName\":\"" + name + "\",\"isHoliday\":\"Y\",\"locdate\":" + ymd + ",\"seq\":1}";
    }

    private List<String> rows(int year, HolidaySource source) {
        return store.ofYear(year, source).stream().map(r -> r.date() + " " + r.name() + (r.substitute() ? " [대체]" : "")).toList();
    }

    private void insert(String date, String name, HolidaySource source) {
        jdbc.update("INSERT INTO site_holidays (holiday_date, name, source) VALUES (?::date, ?, ?)", date, name, source.name());
    }

    @Test
    void 받으면_그_해_공공데이터_줄이_응답과_같아지고_기본값은_지우며_회사_휴무일은_그대로() {
        insert("2040-01-01", "1월 1일", HolidaySource.SEED);
        insert("2040-05-05", "어린이날", HolidaySource.SEED);
        insert("2040-03-02", "창립기념일", HolidaySource.OPERATOR);
        insert("2040-03-01", "회사 행사", HolidaySource.OPERATOR); // 공휴일과 같은 날 회사 휴무일
        month(Y1, 1, item("1월1일", "20400101"));
        month(Y1, 3, item("삼일절", "20400301"));
        month(Y1, 5, item("어린이날", "20400505"), item("부처님오신날", "20400505"), item("대체공휴일(어린이날)", "20400507"));

        HolidaySyncService.Result result = service(KEY).sync(null, null);

        assertThat(result.outcome()).isEqualTo(HolidaySyncService.Outcome.OK);
        assertThat(result.appliedYears()).containsExactly(Y1);
        assertThat(result.emptyYears()).as("내년 자료가 아직 없음").containsExactly(Y2);
        assertThat(rows(Y1, HolidaySource.PUBLIC_DATA)).containsExactly("2040-01-01 1월1일", "2040-03-01 삼일절", "2040-05-05 어린이날", "2040-05-05 부처님오신날",
                "2040-05-07 대체공휴일(어린이날) [대체]");
        assertThat(rows(Y1, HolidaySource.SEED)).as("첫 성공 때 그 해 기본값은 지운다").isEmpty();
        assertThat(rows(Y1, HolidaySource.OPERATOR)).containsExactly("2040-03-01 회사 행사", "2040-03-02 창립기념일");
        assertThat(result.changed()).as("넣기 5 + 기본값 지움 2").isEqualTo(7);
        assertThat(calls.get()).as("두 해 × 열두 달").isEqualTo(24);
        assertThat(serviceOfStore().calendar().businessDay(LocalDate.of(2040, 3, 2))).as("판정은 회사 휴무일도 쉰다").isFalse();
    }

    @Test
    void 다시_받으면_새_날_넣기_사라진_날_지우기_이름_바꾸기만_하고_회사_휴무일은_건드리지_않는다() {
        month(Y1, 1, item("1월1일", "20400101"));
        month(Y1, 6, item("현충일", "20400606"), item("임시공휴일", "20400610"));
        HolidaySyncService sync = service(KEY);
        sync.sync(null, null);
        insert("2040-06-10", "회사 휴무", HolidaySource.OPERATOR);

        bodies.clear();
        month(Y1, 1, item("신정", "20400101"));                     // 이름 바꿈
        month(Y1, 6, item("현충일", "20400606"));                   // 임시공휴일 사라짐
        month(Y1, 8, item("광복절", "20400815"), item("대체공휴일(광복절)", "20400816")); // 새 날
        HolidaySyncService.Result again = sync.sync(null, null);

        assertThat(again.outcome()).isEqualTo(HolidaySyncService.Outcome.OK);
        assertThat(rows(Y1, HolidaySource.PUBLIC_DATA)).containsExactly("2040-01-01 신정", "2040-06-06 현충일", "2040-08-15 광복절", "2040-08-16 대체공휴일(광복절) [대체]");
        assertThat(rows(Y1, HolidaySource.OPERATOR)).containsExactly("2040-06-10 회사 휴무");
        assertThat(again.changed()).as("이름 1 + 지움 1 + 넣기 2").isEqualTo(4);

        HolidaySyncService.Result same = sync.sync(null, null);
        assertThat(same.changed()).as("같은 응답이면 바뀌는 줄이 없다").isZero();
        assertThat(rows(Y1, HolidaySource.PUBLIC_DATA)).hasSize(4);
    }

    @Test
    void 응답이_빈_해는_그대로_두고_기본값도_지우지_않는다() {
        insert("2041-01-01", "1월 1일", HolidaySource.SEED);
        month(Y1, 1, item("1월1일", "20400101"));

        HolidaySyncService.Result result = service(KEY).sync(null, null);

        assertThat(result.emptyYears()).containsExactly(Y2);
        assertThat(rows(Y2, HolidaySource.SEED)).containsExactly("2041-01-01 1월 1일");
        assertThat(rows(Y2, HolidaySource.PUBLIC_DATA)).isEmpty();
    }

    @Test
    void 한_해가_실패하면_그_해만_그대로_두고_다른_해는_반영한다_모두_실패면_FAILED() {
        insert("2041-01-01", "1월 1일", HolidaySource.SEED);
        insert("2040-02-01", "옛 공휴일", HolidaySource.PUBLIC_DATA);
        month(Y1, 1, item("1월1일", "20400101"));
        month(Y2, 1, item("1월1일", "20410101"));
        failingYears.add(Y2);

        HolidaySyncService.Result partial = service(KEY).sync(null, null);

        assertThat(partial.outcome()).isEqualTo(HolidaySyncService.Outcome.PARTIAL);
        assertThat(partial.appliedYears()).containsExactly(Y1);
        assertThat(partial.failedYears()).containsExactly(Y2);
        assertThat(partial.failures()).containsEntry(Y2, "ERROR");
        assertThat(rows(Y1, HolidaySource.PUBLIC_DATA)).containsExactly("2040-01-01 1월1일");
        assertThat(rows(Y2, HolidaySource.SEED)).as("실패한 해는 그대로").containsExactly("2041-01-01 1월 1일");
        assertThat(store.lastSync()).hasValueSatisfying(s -> {
            assertThat(s.outcome()).isEqualTo("PARTIAL");
            assertThat(s.appliedYears()).containsExactly(Y1);
            assertThat(s.failedYears()).containsExactly(Y2);
        });

        failingYears.add(Y1);
        HolidaySyncService.Result failed = service(KEY).sync(null, null);
        assertThat(failed.outcome()).isEqualTo(HolidaySyncService.Outcome.FAILED);
        assertThat(failed.changed()).isZero();
        assertThat(rows(Y1, HolidaySource.PUBLIC_DATA)).as("모두 실패면 표는 그대로").containsExactly("2040-01-01 1월1일");
    }

    @Test
    void 인증키가_없으면_부르지_않고_NO_KEY_로_남는다() {
        HolidaySyncService.Result result = service("").sync(null, null);

        assertThat(result.outcome()).isEqualTo(HolidaySyncService.Outcome.NO_KEY);
        assertThat(calls.get()).isZero();
        assertThat(store.lastSync()).hasValueSatisfying(s -> assertThat(s.outcome()).isEqualTo("NO_KEY"));
        assertThat(service("").keyConfigured()).isFalse();
    }

    @Test
    void 운영자_받기는_감사를_남기고_예약_받기는_남기지_않는다() {
        month(Y1, 1, item("1월1일", "20400101"));
        long operator = operator();

        service(KEY).sync(null, null);
        assertThat(audits).isEmpty();
        service(KEY).sync(operator, "127.0.0.1");

        assertThat(audits).singleElement().satisfies(a -> {
            assertThat(a.action()).isEqualTo(AuditActions.SITE_HOLIDAY_SYNC);
            assertThat(a.targetType()).isEqualTo("SITE_HOLIDAY");
            assertThat(a.detail()).containsEntry("outcome", "OK");
        });
        assertThat(store.lastSync()).hasValueSatisfying(s -> assertThat(s.ranByName()).isNotNull());
    }

    @Test
    void 공공데이터_설정에서_바꾼_주소_요청_글자_틀_칸_이름으로_부른다() {
        long operator = operator();
        endpoints.saveHoliday("https://holidays.example.org/api/rest/", "key={serviceKey}&y={year}&m={month}&type=json", "ymd", "title", operator, "ip");
        bodies.put(Y1 + "-01", "{\"item\":{\"title\":\"새해\",\"ymd\":\"20400101\"}}");

        service(KEY).sync(null, null);

        assertThat(requested).isNotEmpty().allSatisfy(uri -> assertThat(uri.toString()).startsWith("https://holidays.example.org/api/rest?key=" + KEY + "&y="));
        assertThat(rows(Y1, HolidaySource.PUBLIC_DATA)).containsExactly("2040-01-01 새해");

        endpoints.reset(PublicDataEndpointService.HOLIDAY, operator, "ip");
        requested.clear();
        service(KEY).sync(null, null);
        assertThat(requested).allSatisfy(uri -> assertThat(uri.toString()).startsWith(SpecialDayApi.DEFAULT_URL + "?serviceKey=" + KEY + "&solYear="));
    }

    @Test
    void 연결_시험은_지금_주소로_올해_1월_한_달만_부르고_표는_바꾸지_않는다() {
        long operator = operator();
        month(Y1, 1, item("1월1일", "20400101"));

        PublicDataSettingsView.Test ok = service(KEY).probe(operator);

        assertThat(ok.result()).isEqualTo(PublicDataSettingsView.Result.OK);
        assertThat(requested).singleElement().satisfies(uri -> assertThat(uri.toString()).contains("solYear=2040&solMonth=01"));
        assertThat(rows(Y1, HolidaySource.PUBLIC_DATA)).isEmpty();
        assertThat(endpoints.view().holiday().lastTest().result()).isEqualTo(PublicDataSettingsView.Result.OK);

        failingYears.add(Y1);
        assertThat(service(KEY).probe(operator).result()).isEqualTo(PublicDataSettingsView.Result.ERROR);
        assertThat(service("").probe(operator).result()).isEqualTo(PublicDataSettingsView.Result.NO_KEY);
    }

    @Test
    void 배치는_키가_없으면_건너뜀_실패한_해가_있으면_예외로_끝나_실패_알림을_탄다() {
        assertThat(new HolidaySyncJob(service("")).definition().key()).isEqualTo("holiday-sync");
        assertThat(com.community.batch.core.BatchCatalog.HOLIDAY_SYNC.isJava()).isTrue();
        assertThat(com.community.batch.core.BatchCatalog.HOLIDAY_SYNC.defaultCron()).as("매월 1일 새벽").isEqualTo("0 15 3 1 * *");
        assertThat(com.community.batch.core.BatchCatalog.HOLIDAY_SYNC.defaultEnabled()).isTrue();

        failingYears.add(Y2);
        month(Y1, 1, item("1월1일", "20400101"));
        HolidaySyncService.Result partial = service(KEY).sync(null, null);
        assertThat(partial.outcome()).isEqualTo(HolidaySyncService.Outcome.PARTIAL);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> HolidaySyncJob.summarize(partial))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(Y2 + "(ERROR)").hasMessageNotContaining(KEY);
        assertThat(HolidaySyncJob.summarize(service("").sync(null, null)).message().key()).as("키 없음 — 실패가 아니라 건너뜀").isEqualTo("batch.result.holidaySync.noKey");
        failingYears.clear();
        assertThat(HolidaySyncJob.summarize(service(KEY).sync(null, null)).message().key()).as("같은 응답 — 바뀐 줄 없음").isEqualTo("batch.result.holidaySync.unchanged");
        assertThat(rows(Y1, HolidaySource.PUBLIC_DATA)).as("반영한 해는 남는다").containsExactly("2040-01-01 1월1일");
    }

    /** 다른 시험이 쓰는 SiteHolidayService 와 같은 판정 — 이 시험의 저장소로 */
    private SiteHolidayService serviceOfStore() {
        return JpaTestContext.transactional(new SiteHolidayService(store, audit, clocks));
    }

    @Test
    void 판정은_표의_모든_출처를_쉰다() {
        insert("2040-04-02", "기본값", HolidaySource.SEED);
        insert("2040-04-03", "회사", HolidaySource.OPERATOR);
        insert("2040-04-04", "공공", HolidaySource.PUBLIC_DATA);
        var cal = serviceOfStore().calendar();
        assertThat(cal.businessDay(LocalDate.of(2040, 4, 2))).isFalse();
        assertThat(cal.businessDay(LocalDate.of(2040, 4, 3))).isFalse();
        assertThat(cal.businessDay(LocalDate.of(2040, 4, 4))).isFalse();
        assertThat(cal.onOrAfter(LocalDate.of(2040, 4, 2))).isEqualTo(LocalDate.of(2040, 4, 5));
    }
}
