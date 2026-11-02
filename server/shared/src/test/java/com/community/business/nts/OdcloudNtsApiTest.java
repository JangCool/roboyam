package com.community.business.nts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.community.business.entity.NtsBizStatus;
import com.community.business.entity.NtsValidity;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
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
 * 공공데이터포털 국세청 API 해석(business-contract 「국세청 진위확인」 — 2026-11-02 실제 호출로 확인한 응답 모양)을 HTTP 흉내({@link MockRestServiceServer})로 확인한다.
 * 실제 국세청은 부르지 않는다. 진위 {@code valid:"01"} 일치(+ 같은 응답의 상태) · {@code "02"} 불일치(상태 없음) · 401 키 틀림 · 키는 호출마다 읽는다 · 키·이름·번호는 로그에 없다.
 */
@ExtendWith(OutputCaptureExtension.class)
class OdcloudNtsApiTest {

    private static final String BASE = "https://api.odcloud.kr";
    private static final String KEY = "test-service-key-must-not-leak";
    private static final String BIZ_NO = "1234567891";
    private static final String OWNER = "홍길동비밀";
    private static final String COMPANY = "(주)파이언넷";

    private MockRestServiceServer server;
    private OdcloudNtsApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        api = new OdcloudNtsApi(builder.build());
    }

    private static String url(String path) {
        return BASE + path + "?serviceKey=" + KEY + "&returnType=JSON";
    }

    @Test
    void 진위_일치_01_이면_같은_응답의_상태로_채우고_번호_개업일_대표자_상호를_UTF8_JSON_으로_보낸다(CapturedOutput output) {
        server.expect(requestTo(url(OdcloudNtsApi.VALIDATE_PATH))).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.businesses[0].b_no").value(BIZ_NO))
                .andExpect(jsonPath("$.businesses[0].start_dt").value("20210302"))
                .andExpect(jsonPath("$.businesses[0].p_nm").value(OWNER))
                .andExpect(jsonPath("$.businesses[0].b_nm").value(COMPANY))
                .andRespond(withSuccess("{\"status_code\":\"OK\",\"request_cnt\":1,\"valid_cnt\":1,\"data\":[{\"b_no\":\"" + BIZ_NO + "\",\"valid\":\"01\","
                        + "\"request_param\":{\"b_no\":\"" + BIZ_NO + "\",\"start_dt\":\"20210302\",\"p_nm\":\"" + OWNER + "\",\"b_nm\":\"" + COMPANY + "\"},"
                        + "\"status\":{\"b_no\":\"" + BIZ_NO + "\",\"b_stt\":\"계속사업자\",\"b_stt_cd\":\"01\",\"tax_type\":\"부가가치세 일반과세자\",\"tax_type_cd\":\"01\","
                        + "\"end_dt\":\"\",\"utcc_yn\":\"N\"}}]}", MediaType.APPLICATION_JSON));

        NtsLookup result = api.validate(KEY, BIZ_NO, LocalDate.of(2021, 3, 2), OWNER, COMPANY);

        server.verify();
        assertThat(result.validity()).isEqualTo(NtsValidity.MATCH);
        assertThat(result.status()).isEqualTo(NtsBizStatus.CONTINUING);
        assertThat(result.taxType()).isEqualTo("부가가치세 일반과세자");
        assertThat(result.closedOn()).isNull();
        assertThat(result.message()).isNull();
        assertThat(output.getAll()).doesNotContain(KEY).doesNotContain(OWNER).doesNotContain(BIZ_NO);
    }

    @Test
    void 진위_일치에_휴업_상태면_상태와_휴업일을_싣는다() {
        server.expect(requestTo(url(OdcloudNtsApi.VALIDATE_PATH))).andRespond(withSuccess("{\"data\":[{\"b_no\":\"" + BIZ_NO + "\",\"valid\":\"01\","
                + "\"status\":{\"b_stt_cd\":\"02\",\"tax_type\":\"부가가치세 간이과세자\",\"end_dt\":\"20260901\"}}]}", MediaType.APPLICATION_JSON));

        NtsLookup result = api.validate(KEY, BIZ_NO, LocalDate.of(2021, 3, 2), OWNER, COMPANY);

        assertThat(result.validity()).isEqualTo(NtsValidity.MATCH);
        assertThat(result.status()).isEqualTo(NtsBizStatus.PAUSED);
        assertThat(result.closedOn()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void 진위_불일치_02_는_상태_없이_MISMATCH() {
        server.expect(requestTo(url(OdcloudNtsApi.VALIDATE_PATH))).andRespond(withSuccess("{\"status_code\":\"OK\",\"request_cnt\":1,\"valid_cnt\":0,\"data\":[{\"b_no\":\""
                + BIZ_NO + "\",\"valid\":\"02\",\"valid_msg\":\"확인할 수 없습니다.\",\"request_param\":{\"b_no\":\"" + BIZ_NO + "\"}}]}", MediaType.APPLICATION_JSON));

        NtsLookup result = api.validate(KEY, BIZ_NO, LocalDate.of(2021, 3, 2), OWNER, COMPANY);

        assertThat(result.validity()).isEqualTo(NtsValidity.MISMATCH);
        assertThat(result.status()).isEqualTo(NtsBizStatus.UNAVAILABLE);
        assertThat(result.message()).isNull();
        assertThat(result.taxType()).isNull();
    }

    @Test
    void 키가_없거나_틀려_401_이면_조회_불가_KEY_INVALID_이고_로그에_키가_없다(CapturedOutput output) {
        String body = "{\"code\":-401,\"msg\":\"인증키는 필수 항목 입니다.\"}";
        server.expect(requestTo(url(OdcloudNtsApi.VALIDATE_PATH))).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body(body).contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(OdcloudNtsApi.STATUS_PATH))).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body(body).contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(OdcloudNtsApi.STATUS_PATH))).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body(body).contentType(MediaType.APPLICATION_JSON));

        NtsLookup validate = api.validate(KEY, BIZ_NO, LocalDate.of(2021, 3, 2), OWNER, COMPANY);
        NtsLookup status = api.status(KEY, BIZ_NO);
        OdcloudNtsApi.Probe probe = api.probe(KEY, BIZ_NO);

        server.verify();
        assertThat(validate.validity()).isEqualTo(NtsValidity.UNAVAILABLE);
        assertThat(validate.status()).isEqualTo(NtsBizStatus.UNAVAILABLE);
        assertThat(validate.message()).isEqualTo("KEY_INVALID");
        assertThat(status.status()).isEqualTo(NtsBizStatus.UNAVAILABLE);
        assertThat(status.validity()).isEqualTo(NtsValidity.UNCHECKED);
        assertThat(status.message()).isEqualTo("KEY_INVALID");
        assertThat(probe).isEqualTo(OdcloudNtsApi.Probe.KEY_INVALID);
        assertThat(output.getAll()).contains("HTTP 401").doesNotContain(KEY).doesNotContain(OWNER).doesNotContain(BIZ_NO).doesNotContain("인증키는");
    }

    @Test
    void 상태조회_01_계속_03_폐업_빈값_없는_번호() {
        server.expect(requestTo(url(OdcloudNtsApi.STATUS_PATH))).andExpect(jsonPath("$.b_no[0]").value(BIZ_NO))
                .andRespond(withSuccess("{\"data\":[{\"b_no\":\"" + BIZ_NO + "\",\"b_stt_cd\":\"01\",\"tax_type\":\"부가가치세 일반과세자\",\"end_dt\":\"\"}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(OdcloudNtsApi.STATUS_PATH)))
                .andRespond(withSuccess("{\"data\":[{\"b_stt_cd\":\"03\",\"tax_type\":\"부가가치세 일반과세자\",\"end_dt\":\"20260630\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(OdcloudNtsApi.STATUS_PATH)))
                .andRespond(withSuccess("{\"data\":[{\"b_stt_cd\":\"\",\"tax_type\":\"국세청에 등록되지 않은 사업자등록번호입니다.\"}]}", MediaType.APPLICATION_JSON));

        assertThat(api.status(KEY, BIZ_NO)).isEqualTo(new NtsLookup(NtsBizStatus.CONTINUING, "부가가치세 일반과세자", null, null));
        NtsLookup closed = api.status(KEY, BIZ_NO);
        assertThat(closed.status()).isEqualTo(NtsBizStatus.CLOSED);
        assertThat(closed.closedOn()).isEqualTo(LocalDate.of(2026, 6, 30));
        NtsLookup missing = api.status(KEY, BIZ_NO);
        assertThat(missing.status()).isEqualTo(NtsBizStatus.NOT_FOUND);
        assertThat(missing.taxType()).isNull();
        assertThat(missing.validity()).isEqualTo(NtsValidity.UNCHECKED);
    }

    @Test
    void 연결_실패는_TIMEOUT_서버_오류와_모양이_틀린_응답은_ERROR() {
        server.expect(requestTo(url(OdcloudNtsApi.STATUS_PATH))).andRespond(request -> {
            throw new java.net.SocketTimeoutException("read timed out");
        });
        server.expect(requestTo(url(OdcloudNtsApi.VALIDATE_PATH))).andRespond(withServerError());
        server.expect(requestTo(url(OdcloudNtsApi.VALIDATE_PATH))).andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(OdcloudNtsApi.STATUS_PATH))).andRespond(request -> {
            throw new java.net.ConnectException("refused");
        });
        server.expect(requestTo(url(OdcloudNtsApi.STATUS_PATH))).andRespond(withSuccess("{\"data\":[{\"b_stt_cd\":\"01\"}]}", MediaType.APPLICATION_JSON));

        assertThat(api.status(KEY, BIZ_NO).message()).isEqualTo("TIMEOUT");
        NtsLookup error = api.validate(KEY, BIZ_NO, LocalDate.of(2021, 3, 2), OWNER, COMPANY);
        assertThat(error.validity()).isEqualTo(NtsValidity.UNAVAILABLE);
        assertThat(error.message()).isEqualTo("ERROR");
        assertThat(api.validate(KEY, BIZ_NO, LocalDate.of(2021, 3, 2), OWNER, COMPANY).message()).isEqualTo("ERROR");
        assertThat(api.probe(KEY, BIZ_NO)).isEqualTo(OdcloudNtsApi.Probe.TIMEOUT);
        assertThat(api.probe(KEY, BIZ_NO)).isEqualTo(OdcloudNtsApi.Probe.OK);
        server.verify();
    }

    @Test
    void 공공데이터포털_구현은_호출마다_키를_읽고_키가_없으면_부르지_않고_NO_KEY() {
        AtomicReference<String> key = new AtomicReference<>(null);
        OdcloudNtsStatusClient client = new OdcloudNtsStatusClient(api, key::get);

        NtsLookup none = client.validate(BIZ_NO, LocalDate.of(2021, 3, 2), OWNER, COMPANY);
        assertThat(none.validity()).isEqualTo(NtsValidity.UNAVAILABLE);
        assertThat(none.message()).isEqualTo("NO_KEY");
        key.set("  ");
        assertThat(client.lookup(BIZ_NO).message()).isEqualTo("NO_KEY");
        server.verify(); // 아무것도 부르지 않았다

        key.set(KEY); // 화면에서 키를 저장한 것처럼 — 재기동 없이 다음 조회부터
        server.expect(requestTo(url(OdcloudNtsApi.STATUS_PATH)))
                .andRespond(withSuccess("{\"data\":[{\"b_stt_cd\":\"01\",\"tax_type\":\"부가가치세 일반과세자\"}]}", MediaType.APPLICATION_JSON));
        assertThat(client.lookup(BIZ_NO).status()).isEqualTo(NtsBizStatus.CONTINUING);
        server.verify();
    }

    @Test
    void 가짜_구현은_끝에서_둘째_자리로_진위와_상태를_고른다() {
        FakeNtsStatusClient fake = new FakeNtsStatusClient();
        LocalDate opened = LocalDate.of(2021, 3, 2);

        assertThat(fake.validate("1234567851", opened, OWNER, COMPANY)).isEqualTo(NtsLookup.mismatch());
        assertThat(fake.lookup("1234567851").status()).isEqualTo(NtsBizStatus.CONTINUING);
        assertThat(fake.validate("1234567861", opened, OWNER, COMPANY).validity()).isEqualTo(NtsValidity.MISMATCH);
        assertThat(fake.lookup("1234567861").status()).isEqualTo(NtsBizStatus.NOT_FOUND);
        NtsLookup paused = fake.validate("1234567871", opened, OWNER, COMPANY);
        assertThat(paused.validity()).isEqualTo(NtsValidity.MATCH);
        assertThat(paused.status()).isEqualTo(NtsBizStatus.PAUSED);
        assertThat(fake.validate("1234567881", opened, OWNER, COMPANY).status()).isEqualTo(NtsBizStatus.CLOSED);
        NtsLookup down = fake.validate("1234567891", opened, OWNER, COMPANY);
        assertThat(down.validity()).isEqualTo(NtsValidity.UNAVAILABLE);
        assertThat(down.status()).isEqualTo(NtsBizStatus.UNAVAILABLE);
        assertThat(fake.lookup("1234567891").validity()).isEqualTo(NtsValidity.UNCHECKED);
        NtsLookup ok = fake.validate("1234567801", opened, OWNER, COMPANY);
        assertThat(ok.validity()).isEqualTo(NtsValidity.MATCH);
        assertThat(ok.status()).isEqualTo(NtsBizStatus.CONTINUING);
    }
}
