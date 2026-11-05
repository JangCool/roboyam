package com.community.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.community.holiday.client.SpecialDayApi;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 공공데이터 특일 정보 공휴일 조회({@link SpecialDayApi} — data.go.kr 15012690 {@code getRestDeInfo}) 해석을 HTTP 흉내({@link MockRestServiceServer})로 확인한다. 실제 data.go.kr 은 부르지 않는다.
 * 여럿이면 배열 · 하나면 객체 · 없으면 빈 글자 · 결과 코드 오류(키 오류 30 / 그 밖) · XML 오류 응답 · 401 · 읽을 수 없는 본문 · 인증키 인코딩(Encoding 키 풀기, {@code +}{@code /}{@code =}) ·
 * 바꾼 주소·요청 글자 틀·응답 칸 이름 · 공휴일 아님(N) 거르기 · 대체공휴일 · 한 달 실패면 그 해 실패 · 키는 로그에 없다.
 */
@ExtendWith(OutputCaptureExtension.class)
class SpecialDayApiTest {

    private static final String KEY = "ab+cd/ef==";
    private static final String KEY_ENCODED = "ab%2Bcd%2Fef%3D%3D";

    private MockRestServiceServer server;
    private SpecialDayApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        api = new SpecialDayApi(builder.build());
    }

    private static String url(int year, int month) {
        return SpecialDayApi.DEFAULT_URL + "?serviceKey=" + KEY_ENCODED + "&solYear=" + year + "&solMonth=" + "%02d".formatted(month) + "&pageNo=1&numOfRows=100&_type=json";
    }

    private static String ok(String items) {
        return "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE.\"},\"body\":{\"items\":" + items
                + ",\"numOfRows\":100,\"pageNo\":1,\"totalCount\":1}}}";
    }

    private static String item(String name, long locdate) {
        return "{\"dateKind\":\"01\",\"dateName\":\"" + name + "\",\"isHoliday\":\"Y\",\"locdate\":" + locdate + ",\"seq\":1}";
    }

    @Test
    void 여럿이면_배열_대체공휴일은_이름으로_알고_기본_주소_틀로_키를_폼_인코딩해_부른다(CapturedOutput output) {
        server.expect(requestTo(url(2026, 3))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(ok("{\"item\":[" + item("삼일절", 20260301) + "," + item("대체공휴일(삼일절)", 20260302) + "]}"), MediaType.APPLICATION_JSON));

        SpecialDayApi.Fetch fetch = api.month(KEY, 2026, 3, SpecialDayApi.Endpoint.defaults());

        server.verify();
        assertThat(fetch.ok()).isTrue();
        assertThat(fetch.items()).containsExactly(new SpecialDayApi.Item(LocalDate.of(2026, 3, 1), "삼일절", false),
                new SpecialDayApi.Item(LocalDate.of(2026, 3, 2), "대체공휴일(삼일절)", true));
        assertThat(output.getAll()).doesNotContain(KEY).doesNotContain(KEY_ENCODED);
    }

    @Test
    void 하나면_객체_없으면_items_가_빈_글자_빈_본문도_빈_목록() {
        server.expect(requestTo(url(2026, 1))).andRespond(withSuccess(ok("{\"item\":" + item("1월1일", 20260101) + "}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(2026, 4))).andRespond(withSuccess(ok("\"\""), MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(2026, 11))).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThat(api.month(KEY, 2026, 1, SpecialDayApi.Endpoint.defaults()).items()).containsExactly(new SpecialDayApi.Item(LocalDate.of(2026, 1, 1), "1월1일", false));
        SpecialDayApi.Fetch empty = api.month(KEY, 2026, 4, SpecialDayApi.Endpoint.defaults());
        assertThat(empty.ok()).isTrue();
        assertThat(empty.items()).isEmpty();
        assertThat(api.month(KEY, 2026, 11, SpecialDayApi.Endpoint.defaults()).items()).isEmpty();
        server.verify();
    }

    @Test
    void 공휴일_아님_N_은_거르고_같은_날_둘이면_둘_다() {
        server.expect(requestTo(url(2025, 5))).andRespond(withSuccess(ok("{\"item\":[" + item("어린이날", 20250505) + "," + item("부처님오신날", 20250505)
                + ",{\"dateName\":\"어버이날\",\"isHoliday\":\"N\",\"locdate\":20250508}]}"), MediaType.APPLICATION_JSON));

        assertThat(api.month(KEY, 2025, 5, SpecialDayApi.Endpoint.defaults()).items()).extracting(SpecialDayApi.Item::name).containsExactly("어린이날", "부처님오신날");
    }

    @Test
    void 결과_코드_30_은_키_오류_그_밖_코드는_오류() {
        server.expect(requestTo(url(2026, 1))).andRespond(withSuccess("{\"response\":{\"header\":{\"resultCode\":\"30\",\"resultMsg\":\"SERVICE_KEY_IS_NOT_REGISTERED_ERROR\"}}}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(2026, 2))).andRespond(withSuccess("{\"response\":{\"header\":{\"resultCode\":\"99\",\"resultMsg\":\"UNKNOWN_ERROR\"}}}",
                MediaType.APPLICATION_JSON));

        assertThat(api.month(KEY, 2026, 1, SpecialDayApi.Endpoint.defaults()).failure()).isEqualTo("KEY_INVALID");
        assertThat(api.month(KEY, 2026, 2, SpecialDayApi.Endpoint.defaults()).failure()).isEqualTo("ERROR");
    }

    @Test
    void XML_오류_응답과_401_은_키_오류_읽을_수_없는_본문은_오류() {
        server.expect(requestTo(url(2026, 1))).andRespond(withSuccess("<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>"
                + "<returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>",
                MediaType.TEXT_XML));
        server.expect(requestTo(url(2026, 2))).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("Unauthorized"));
        server.expect(requestTo(url(2026, 3))).andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(2026, 4))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(api.month(KEY, 2026, 1, SpecialDayApi.Endpoint.defaults()).failure()).isEqualTo("KEY_INVALID");
        assertThat(api.month(KEY, 2026, 2, SpecialDayApi.Endpoint.defaults()).failure()).isEqualTo("KEY_INVALID");
        assertThat(api.month(KEY, 2026, 3, SpecialDayApi.Endpoint.defaults()).failure()).isEqualTo("ERROR");
        assertThat(api.month(KEY, 2026, 4, SpecialDayApi.Endpoint.defaults()).failure()).isEqualTo("ERROR");
    }

    @Test
    void 이미_인코딩된_Encoding_키는_풀어서_한_번만_인코딩한다() {
        server.expect(requestTo(url(2026, 1))).andRespond(withSuccess(ok("\"\""), MediaType.APPLICATION_JSON));

        assertThat(api.month(KEY_ENCODED, 2026, 1, SpecialDayApi.Endpoint.defaults()).ok()).isTrue();
        server.verify();
    }

    @Test
    void 바꾼_주소_요청_글자_틀_응답_칸_이름으로_부르고_읽는다() {
        SpecialDayApi.Endpoint custom = new SpecialDayApi.Endpoint("https://holidays.example.org/v2/rest", "key={serviceKey}&y={year}&m={month}&format=json&rows={numOfRows}",
                "ymd", "title");
        server.expect(requestTo("https://holidays.example.org/v2/rest?key=" + KEY_ENCODED + "&y=2026&m=09&format=json&rows=100"))
                .andRespond(withSuccess(ok("{\"item\":{\"title\":\"추석\",\"ymd\":\"20260925\"}}"), MediaType.APPLICATION_JSON));

        assertThat(api.month(KEY, 2026, 9, custom).items()).containsExactly(new SpecialDayApi.Item(LocalDate.of(2026, 9, 25), "추석", false));
        server.verify();
    }

    @Test
    void 칸_이름이_맞지_않아_줄을_읽지_못하면_그_달은_오류() {
        SpecialDayApi.Endpoint wrong = new SpecialDayApi.Endpoint(SpecialDayApi.DEFAULT_URL, SpecialDayApi.DEFAULT_QUERY_TEMPLATE, "date", "dateName");
        server.expect(requestTo(url(2026, 9))).andRespond(withSuccess(ok("{\"item\":" + item("추석", 20260925) + "}"), MediaType.APPLICATION_JSON));

        assertThat(api.month(KEY, 2026, 9, wrong).failure()).isEqualTo("ERROR");
    }

    @Test
    void 한_해는_열두_달을_부르고_한_달이라도_실패하면_그_해_전체가_실패() {
        server.expect(times(1), requestTo(url(2027, 1))).andRespond(withSuccess(ok("{\"item\":" + item("1월1일", 20270101) + "}"), MediaType.APPLICATION_JSON));
        server.expect(times(1), requestTo(url(2027, 2))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        SpecialDayApi.Fetch fetch = api.year(KEY, 2027, SpecialDayApi.Endpoint.defaults());

        assertThat(fetch.ok()).isFalse();
        assertThat(fetch.items()).as("부분 응답은 쓰지 않는다").isEmpty();
        server.verify();
    }
}
