package com.community.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.audit.service.AuditLogService;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.service.GroupBuyBusinessDays;
import com.community.holiday.entity.HolidaySource;
import com.community.holiday.exception.HolidayNotEditableException;
import com.community.holiday.exception.HolidayNotFoundException;
import com.community.holiday.repository.master.SiteHolidayStore;
import com.community.holiday.service.SiteHolidayService;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.JpaTestContext;
import com.community.testsupport.MutableTestClock;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 사이트 쉬는 날 표 하나(사용자 지시 2026-11-05 — {@link SiteHolidayService}) — 회사 휴무일 더하기·지우기 규칙(날짜·이름 1~30자 필수, 하루 한 줄, 공공데이터·기본값은 읽기만, 감사),
 * 달력 읽기(사이트에는 공휴일만 · 관리자는 회사 휴무일도, 최대 2년), 같은 날 공휴일 + 회사 휴무일 두 줄, 공구 영업일이 같은 표를 본다.
 * 실제 PostgreSQL(V150). 2042 년 줄만 쓰고 끝나면 지운다.
 */
class SiteHolidayServiceTest {

    private static final int YEAR = 2042;

    private DataSource ds;
    private JdbcTemplate jdbc;
    private SiteHolidayStore store;
    private SiteHolidayService service;
    private final List<AuditEvent> audits = new ArrayList<>();
    private long operator;

    @BeforeEach
    void setUp() {
        ds = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(ds);
        cleanup();
        store = JpaTestContext.create(SiteHolidayStore.class);
        AuditLogService audit = new AuditLogService(null, null) {
            @Override
            public void record(AuditEvent event) {
                audits.add(event);
            }
        };
        service = JpaTestContext.transactional(new SiteHolidayService(store, audit,
                new StaticListableBeanFactory(Map.of("clock", new MutableTestClock(Instant.parse("2042-02-01T00:00:00Z")))).getBeanProvider(Clock.class)));
        operator = 9_500_000_000L + System.nanoTime() % 1_000_000;
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, now(), now())", operator,
                "sh-" + operator + "@test.local", "sh" + (operator % 1_000_000_000L));
    }

    @AfterEach
    void cleanup() {
        if (jdbc != null) {
            jdbc.update("DELETE FROM site_holidays WHERE holiday_date BETWEEN ? AND ?", LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31));
        }
    }

    private void insert(String date, String name, HolidaySource source) {
        jdbc.update("INSERT INTO site_holidays (holiday_date, name, source) VALUES (?::date, ?, ?)", date, name, source.name());
    }

    @Test
    void 회사_휴무일은_날짜와_이름이_필수_이름은_앞뒤_공백을_지우고_1_30자_하루에_한_줄() {
        assertThatThrownBy(() -> service.addCompanyDay(null, "창립기념일", operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("date"));
        assertThatThrownBy(() -> service.addCompanyDay(LocalDate.of(YEAR, 3, 3), "   ", operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("name"));
        assertThatThrownBy(() -> service.addCompanyDay(LocalDate.of(YEAR, 3, 3), "가".repeat(31), operator, "ip"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("name"));

        SiteHolidayStore.Row row = service.addCompanyDay(LocalDate.of(YEAR, 3, 3), "  " + "가".repeat(30) + "  ", operator, "10.0.0.1");
        assertThat(row.source()).isEqualTo(HolidaySource.OPERATOR);
        assertThat(row.name()).isEqualTo("가".repeat(30));
        assertThat(row.createdByName()).isNotNull();
        assertThat(audits).singleElement().satisfies(a -> {
            assertThat(a.action()).isEqualTo(AuditActions.SITE_HOLIDAY_CREATE);
            assertThat(a.targetType()).isEqualTo("SITE_HOLIDAY");
            assertThat(a.targetId()).isEqualTo(String.valueOf(row.id()));
            assertThat(a.detail()).containsEntry("date", "2042-03-03");
        });
        assertThatThrownBy(() -> service.addCompanyDay(LocalDate.of(YEAR, 3, 3), "또", operator, "ip")).as("하루에 한 줄")
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("date"));
        assertThat(AuditRetention.classify(AuditActions.SITE_HOLIDAY_CREATE)).isEqualTo(AuditRetentionClass.GENERAL);
    }

    @Test
    void 같은_날에_공휴일과_회사_휴무일이_함께_있을_수_있다_DB_도_회사_휴무일은_하루_한_줄_30자() {
        insert("2042-05-05", "어린이날", HolidaySource.PUBLIC_DATA);
        insert("2042-05-05", "부처님오신날", HolidaySource.PUBLIC_DATA);
        service.addCompanyDay(LocalDate.of(YEAR, 5, 5), "회사 행사", operator, "ip");

        assertThat(service.year(YEAR)).extracting(r -> r.source() + " " + r.name()).containsExactly("PUBLIC_DATA 어린이날", "PUBLIC_DATA 부처님오신날", "OPERATOR 회사 행사");
        assertThatThrownBy(() -> insert("2042-05-05", "또", HolidaySource.OPERATOR)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> insert("2042-05-06", "가".repeat(31), HolidaySource.OPERATOR)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> insert("2042-05-06", "x", "OTHER")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    private void insert(String date, String name, String source) {
        jdbc.update("INSERT INTO site_holidays (holiday_date, name, source) VALUES (?::date, ?, ?)", date, name, source);
    }

    @Test
    void 회사_휴무일만_지울_수_있다_공공데이터_기본값은_409_없으면_404_감사() {
        insert("2042-06-06", "현충일", HolidaySource.PUBLIC_DATA);
        insert("2042-06-07", "기본값", HolidaySource.SEED);
        long company = service.addCompanyDay(LocalDate.of(YEAR, 6, 8), "휴무", operator, "ip").id();
        long pub = store.ofYear(YEAR, HolidaySource.PUBLIC_DATA).getFirst().id();
        long seed = store.ofYear(YEAR, HolidaySource.SEED).getFirst().id();
        audits.clear();

        assertThatThrownBy(() -> service.deleteCompanyDay(pub, operator, "ip")).isInstanceOf(HolidayNotEditableException.class);
        assertThatThrownBy(() -> service.deleteCompanyDay(seed, operator, "ip")).isInstanceOf(HolidayNotEditableException.class);
        service.deleteCompanyDay(company, operator, "ip");
        assertThatThrownBy(() -> service.deleteCompanyDay(company, operator, "ip")).isInstanceOf(HolidayNotFoundException.class);

        assertThat(service.year(YEAR)).extracting(SiteHolidayStore.Row::name).containsExactly("현충일", "기본값");
        assertThat(audits).singleElement().satisfies(a -> {
            assertThat(a.action()).isEqualTo(AuditActions.SITE_HOLIDAY_DELETE);
            assertThat(a.detail()).containsEntry("name", "휴무");
        });
    }

    @Test
    void 달력_읽기는_사이트에는_공휴일만_관리자는_회사_휴무일도_범위는_최대_2년() {
        insert("2042-09-25", "추석", HolidaySource.PUBLIC_DATA);
        insert("2042-09-26", "기본값 공휴일", HolidaySource.SEED);
        service.addCompanyDay(LocalDate.of(YEAR, 9, 28), "회사 비밀 행사", operator, "ip");
        LocalDate from = LocalDate.of(YEAR, 9, 1);
        LocalDate to = LocalDate.of(YEAR, 9, 30);

        assertThat(service.days(from, to, false)).containsExactly(new SiteHolidayService.Day(LocalDate.of(YEAR, 9, 25), "추석", "HOLIDAY"),
                new SiteHolidayService.Day(LocalDate.of(YEAR, 9, 26), "기본값 공휴일", "HOLIDAY"));
        assertThat(service.days(from, to, true)).extracting(SiteHolidayService.Day::kind).containsExactly("HOLIDAY", "HOLIDAY", "COMPANY");
        assertThat(SiteHolidayService.etag(from, to, service.days(from, to, false))).as("내용이 같으면 같은 지문").isEqualTo(SiteHolidayService.etag(from, to,
                service.days(from, to, false))).hasSize(32);

        assertThatThrownBy(() -> service.days(from, from.plusYears(2).plusDays(1), false)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.days(to, from, false)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.days(null, to, false)).isInstanceOf(ValidationException.class);
        assertThat(service.days(from, from.plusYears(2), false)).as("2년 정확히는 된다").isNotEmpty();
        assertThatThrownBy(() -> SiteHolidayService.date("from", "2042-13-01")).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.getFields()).containsKey("from"));
    }

    @Test
    void 공구_영업일도_같은_표를_본다_회사_휴무일을_더하면_다음_판정부터_쉰다() {
        GroupBuyStore groups = new GroupBuyStore(ds);
        LocalDate monday = LocalDate.of(YEAR, 7, 7);
        assertThat(GroupBuyBusinessDays.plus(LocalDate.of(YEAR, 7, 4), 1, groups.settings().calendar())).isEqualTo(monday);

        service.addCompanyDay(monday, "여름 휴무", operator, "ip");

        assertThat(GroupBuyBusinessDays.plus(LocalDate.of(YEAR, 7, 4), 1, groups.settings().calendar())).as("금 → (토·일·월 휴무) → 화").isEqualTo(monday.plusDays(1));
        assertThat(service.calendar().businessDay(monday)).isFalse();
    }
}
