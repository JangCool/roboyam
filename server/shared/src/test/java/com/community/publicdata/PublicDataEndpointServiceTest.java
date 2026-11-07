package com.community.publicdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.audit.service.AuditLogService;
import com.community.business.nts.NtsProperties;
import com.community.business.nts.NtsProviderKind;
import com.community.business.nts.OdcloudNtsApi;
import com.community.holiday.client.SpecialDayApi;
import com.community.publicdata.dto.response.PublicDataEndpointsView;
import com.community.publicdata.repository.master.PublicDataEndpointStore;
import com.community.publicdata.service.PublicDataEndpointService;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.JpaTestContext;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 공공데이터 API 주소 설정(사용자 지시 2026-11-05 — {@link PublicDataEndpointService}) — 저장·검사(https 만 · 500자 · 끝 / 정리 · 요청 글자 틀 자리 · 칸 이름)·기본값으로 되돌리기·
 * 감사(전후 값)·저장 직후 다음 요청부터 그 주소(국세청은 HTTP 흉내로 바꾼 주소에 가는지). 실제 PostgreSQL. 표는 사이트에 하나라 앞뒤로 비운다.
 */
class PublicDataEndpointServiceTest {

    private DataSource ds;
    private JdbcTemplate jdbc;
    private PublicDataEndpointService service;
    private final List<AuditEvent> audits = new ArrayList<>();
    private long operator;

    @BeforeEach
    void setUp() {
        ds = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(ds);
        jdbc.update("DELETE FROM public_data_endpoints");
        AuditLogService audit = new AuditLogService(null, null) {
            @Override
            public void record(AuditEvent event) {
                audits.add(event);
            }
        };
        service = JpaTestContext.transactional(new PublicDataEndpointService(JpaTestContext.create(PublicDataEndpointStore.class), new NtsProperties(NtsProviderKind.FAKE, "", "https://api.odcloud.kr/", 5), audit,
                new StaticListableBeanFactory(Map.of("clock", Clock.systemUTC())).getBeanProvider(Clock.class)));
        operator = 9_600_000_000L + System.nanoTime() % 1_000_000;
        jdbc.update("INSERT INTO operators (id, email, operator_name, password_hash, approved, created_at, updated_at) VALUES (?, ?, ?, 'x', true, now(), now())", operator,
                "pde-" + operator + "@test.local", "pde" + (operator % 1_000_000_000L));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM public_data_endpoints");
    }

    @Test
    void 처음에는_기본값을_쓰고_화면에_기본값_딱지() {
        PublicDataEndpointsView view = service.view();
        assertThat(view.nts().url()).isEqualTo(new PublicDataEndpointsView.Field("https://api.odcloud.kr", "https://api.odcloud.kr", false));
        assertThat(view.holiday().url().value()).isEqualTo(SpecialDayApi.DEFAULT_URL);
        assertThat(view.holiday().queryTemplate().value()).isEqualTo(SpecialDayApi.DEFAULT_QUERY_TEMPLATE);
        assertThat(view.holiday().dateField().value()).isEqualTo("locdate");
        assertThat(view.holiday().nameField().value()).isEqualTo("dateName");
        assertThat(view.holiday().lastTest()).isNull();
        assertThat(service.ntsBaseUrl()).isEqualTo("https://api.odcloud.kr");
        assertThat(service.holiday()).isEqualTo(SpecialDayApi.Endpoint.defaults());
    }

    @Test
    void 저장하면_끝의_슬래시를_지우고_바로_쓰며_직접_넣음_딱지_빈_칸은_기본값_감사에_전후_값() {
        PublicDataEndpointsView saved = service.saveHoliday("  https://new.data.go.kr/B090041/v2/getRestDeInfo//  ", "", "  ymd ", null, operator, "10.0.0.2");

        assertThat(saved.holiday().url()).isEqualTo(new PublicDataEndpointsView.Field("https://new.data.go.kr/B090041/v2/getRestDeInfo", SpecialDayApi.DEFAULT_URL, true));
        assertThat(saved.holiday().queryTemplate().custom()).isFalse();
        assertThat(saved.holiday().dateField()).isEqualTo(new PublicDataEndpointsView.Field("ymd", "locdate", true));
        assertThat(saved.holiday().nameField().custom()).isFalse();
        assertThat(saved.holiday().updatedBy()).isNotNull();
        assertThat(service.holiday()).isEqualTo(new SpecialDayApi.Endpoint("https://new.data.go.kr/B090041/v2/getRestDeInfo", SpecialDayApi.DEFAULT_QUERY_TEMPLATE, "ymd",
                "dateName"));
        assertThat(audits).singleElement().satisfies(a -> {
            assertThat(a.action()).isEqualTo(AuditActions.PUBLIC_DATA_ENDPOINT_UPDATE);
            assertThat(a.targetType() + "/" + a.targetId()).isEqualTo("PUBLIC_DATA_SETTINGS/holiday");
            assertThat(a.detail().get("fields")).isEqualTo(List.of("url", "dateField"));
            assertThat(((Map<?, ?>) a.detail().get("after")).get("url")).isEqualTo("https://new.data.go.kr/B090041/v2/getRestDeInfo");
        });
        assertThat(AuditRetention.classify(AuditActions.PUBLIC_DATA_ENDPOINT_UPDATE)).isEqualTo(AuditRetentionClass.GENERAL);
    }

    @Test
    void 주소는_https_만_500자_공백_물음표_없이_요청_글자_틀은_세_자리_필수_칸_이름은_영문_숫자_밑줄() {
        assertField(() -> service.saveNts("http://api.odcloud.kr", operator, "ip"), "url");
        assertField(() -> service.saveNts("ftp://x", operator, "ip"), "url");
        assertField(() -> service.saveNts("https://" + "a".repeat(493) + ".kr", operator, "ip"), "url");
        assertField(() -> service.saveNts("https://api odcloud.kr", operator, "ip"), "url");
        assertField(() -> service.saveHoliday("https://x.kr/a?b=1", null, null, null, operator, "ip"), "url");
        assertField(() -> service.saveNts("https://", operator, "ip"), "url");
        assertField(() -> service.saveHoliday(null, "solYear={year}&solMonth={month}", null, null, operator, "ip"), "queryTemplate");
        assertField(() -> service.saveHoliday(null, "serviceKey={serviceKey}&solYear={year}", null, null, operator, "ip"), "queryTemplate");
        assertField(() -> service.saveHoliday(null, "serviceKey={serviceKey}& solYear={year}&solMonth={month}", null, null, operator, "ip"), "queryTemplate");
        assertField(() -> service.saveHoliday(null, "serviceKey={serviceKey}&solYear={year}&solMonth={month}&x=" + "a".repeat(1000), null, null, operator, "ip"),
                "queryTemplate");
        assertField(() -> service.saveHoliday(null, null, "loc-date", null, operator, "ip"), "dateField");
        assertField(() -> service.saveHoliday(null, null, null, "1name", operator, "ip"), "nameField");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public_data_endpoints", Integer.class)).as("검사에 걸리면 저장하지 않는다").isZero();
        assertThat(audits).isEmpty();

        assertThat(service.saveNts("HTTPS://api2.odcloud.kr/", operator, "ip").nts().url().value()).isEqualTo("https://api2.odcloud.kr");
        assertThat(service.saveHoliday(null, "?serviceKey={serviceKey}&solYear={year}&solMonth={month}", null, null, operator, "ip").holiday().queryTemplate().value())
                .as("앞의 ? 는 지운다").isEqualTo("serviceKey={serviceKey}&solYear={year}&solMonth={month}");
    }

    private static void assertField(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String field) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsOnlyKeys(field));
    }

    @Test
    void 기본값으로_되돌리면_모든_칸이_기본값_감사에_되돌리기_전_값() {
        service.saveHoliday("https://x.example.kr/rest", "k={serviceKey}&y={year}&m={month}", "d", "n", operator, "ip");
        service.saveNts("https://nts.example.kr", operator, "ip");
        audits.clear();

        PublicDataEndpointsView view = service.reset(PublicDataEndpointService.HOLIDAY, operator, "ip");

        assertThat(view.holiday().url().custom()).isFalse();
        assertThat(view.holiday().queryTemplate().custom()).isFalse();
        assertThat(view.holiday().dateField().custom()).isFalse();
        assertThat(view.holiday().nameField().custom()).isFalse();
        assertThat(view.nts().url().custom()).as("다른 API 는 그대로").isTrue();
        assertThat(service.holiday()).isEqualTo(SpecialDayApi.Endpoint.defaults());
        assertThat(audits).singleElement().satisfies(a -> {
            assertThat(a.action()).isEqualTo(AuditActions.PUBLIC_DATA_ENDPOINT_RESET);
            assertThat(((Map<?, ?>) a.detail().get("before")).get("url")).isEqualTo("https://x.example.kr/rest");
        });
        service.reset(PublicDataEndpointService.NTS, operator, "ip");
        assertThat(service.ntsBaseUrl()).isEqualTo("https://api.odcloud.kr");
    }

    @Test
    void 국세청_조회는_저장한_주소로_다음_요청부터_간다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.odcloud.kr");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OdcloudNtsApi nts = new OdcloudNtsApi(builder.build(), service::ntsBaseUrl);
        String body = "{\"data\":[{\"b_no\":\"1234567891\",\"b_stt_cd\":\"01\",\"tax_type\":\"일반과세자\"}]}";
        server.expect(requestTo("https://api.odcloud.kr/api/nts-businessman/v1/status?serviceKey=k1&returnType=JSON")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://nts-proxy.example.kr/gw/api/nts-businessman/v1/status?serviceKey=k1&returnType=JSON"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(nts.probe("k1", "1234567891")).isEqualTo(OdcloudNtsApi.Probe.OK);
        service.saveNts("https://nts-proxy.example.kr/gw/", operator, "ip");
        assertThat(nts.probe("k1", "1234567891")).as("재시작 없이 다음 요청부터").isEqualTo(OdcloudNtsApi.Probe.OK);
        server.verify();
    }

    @Test
    void 특일_정보_연결_시험_결과를_남기고_주소를_저장하면_지운다() {
        service.recordHolidayTest("OK", operator);
        assertThat(service.view().holiday().lastTest().result().name()).isEqualTo("OK");
        service.saveHoliday("https://y.example.kr/rest", null, null, null, operator, "ip");
        assertThat(service.view().holiday().lastTest()).isNull();
    }
}
