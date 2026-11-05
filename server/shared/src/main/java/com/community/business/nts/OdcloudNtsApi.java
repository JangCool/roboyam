package com.community.business.nts;

import com.community.business.entity.NtsBizStatus;
import com.community.business.entity.NtsValidity;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 공공데이터포털 「국세청_사업자등록정보 진위확인 및 상태조회」(publicDataPk 15081808) HTTP 호출 — 서비스 키를 <b>호출마다 인자로</b> 받는다
 * (키는 {@link OdcloudNtsStatusClient} 가 화면 저장값 → 환경 변수 차례로 읽어 넘기고, 연결 시험은 저장된 키로 {@link #probe} 를 부른다).
 * <ul>
 *   <li>상태조회 {@code POST /api/nts-businessman/v1/status?serviceKey=} 본문 {@code {"b_no":["…"]}} — 응답 {@code data[0].b_stt_cd}: {@code 01} 계속 ·
 *       {@code 02} 휴업 · {@code 03} 폐업 · 빈 값이면 국세청에 등록되지 않은 번호. {@code end_dt}(YYYYMMDD)는 휴·폐업 일자.</li>
 *   <li>진위확인 {@code POST /api/nts-businessman/v1/validate?serviceKey=} 본문 {@code {"businesses":[{b_no, start_dt(YYYYMMDD), p_nm, b_nm}]}} —
 *       응답 {@code data[0].valid}: {@code 01} 일치(+ {@code status} 에 상태조회 한 줄과 같은 칸) · {@code 02} 불일치({@code valid_msg}, 상태 없음 — 없는 번호도 02).</li>
 *   <li>키가 없거나 틀리면 HTTP 401(또는 403) — 사유 {@code KEY_INVALID}. 응답이 없으면 {@code TIMEOUT}, 그 밖 {@code ERROR}.</li>
 * </ul>
 * 던지지 않는다. 키·요청·응답 본문·등록번호·이름은 로그에 싣지 않는다(실패는 예외 클래스 이름과 HTTP 상태 번호만). 본문은 UTF-8 JSON.
 */
public class OdcloudNtsApi {

    private static final Logger log = LoggerFactory.getLogger(OdcloudNtsApi.class);
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    static final String STATUS_PATH = "/api/nts-businessman/v1/status";
    static final String VALIDATE_PATH = "/api/nts-businessman/v1/validate";

    /** 연결 시험 결과(키 없음 {@code NO_KEY} 는 부르는 쪽이 정한다) */
    public enum Probe {
        OK, KEY_INVALID, TIMEOUT, ERROR
    }

    private final RestClient client;
    /** 지금 쓸 기본 주소(호출마다 — 화면에서 바꾸면 다음 호출부터, {@code PublicDataEndpointService#ntsBaseUrl}). null 이면 클라이언트의 기본 주소 */
    private final Supplier<String> baseUrl;

    public OdcloudNtsApi(RestClient client) {
        this(client, () -> null);
    }

    public OdcloudNtsApi(RestClient client, Supplier<String> baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
    }

    /** 상태조회 — 진위는 {@code UNCHECKED} */
    public NtsLookup status(String serviceKey, String bizNo) {
        try {
            Map<?, ?> row = firstRow(post(STATUS_PATH, serviceKey, Map.of("b_no", List.of(bizNo))));
            return row == null ? NtsLookup.unavailable("ERROR") : statusOf(row);
        } catch (RuntimeException e) {
            return NtsLookup.unavailable(failure("상태조회", e));
        }
    }

    /** 진위확인 — 일치면 같은 응답의 상태를 함께, 불일치면 상태 없이 */
    public NtsLookup validate(String serviceKey, String bizNo, LocalDate openedOn, String ownerName, String companyName) {
        Map<String, Object> business = new LinkedHashMap<>();
        business.put("b_no", bizNo);
        business.put("start_dt", openedOn.format(YMD));
        business.put("p_nm", ownerName);
        business.put("b_nm", companyName);
        try {
            Map<?, ?> row = firstRow(post(VALIDATE_PATH, serviceKey, Map.of("businesses", List.of(business))));
            if (row == null) {
                return NtsLookup.validationUnavailable("ERROR");
            }
            return switch (text(row.get("valid"))) {
                case "01" -> row.get("status") instanceof Map<?, ?> status ? statusOf(status).withValidity(NtsValidity.MATCH)
                        : NtsLookup.unavailable("ERROR").withValidity(NtsValidity.MATCH);
                case "02" -> NtsLookup.mismatch();
                default -> NtsLookup.validationUnavailable("ERROR");
            };
        } catch (RuntimeException e) {
            return NtsLookup.validationUnavailable(failure("진위확인", e));
        }
    }

    /** 연결 시험 — 이 키로 상태조회 한 번(번호는 아무 실재 번호면 된다). 응답 {@code data} 가 오면(번호가 어떻든) 성공이다 */
    public Probe probe(String serviceKey, String bizNo) {
        try {
            Map<?, ?> body = post(STATUS_PATH, serviceKey, Map.of("b_no", List.of(bizNo)));
            return body != null && body.get("data") instanceof List<?> ? Probe.OK : Probe.ERROR;
        } catch (RuntimeException e) {
            return switch (failure("연결 시험", e)) {
                case "KEY_INVALID" -> Probe.KEY_INVALID;
                case "TIMEOUT" -> Probe.TIMEOUT;
                default -> Probe.ERROR;
            };
        }
    }

    private Map<?, ?> post(String path, String serviceKey, Object body) {
        String base = baseUrl.get();
        RestClient.RequestBodyUriSpec spec = client.post();
        RestClient.RequestBodySpec request = base == null || base.isBlank()
                ? spec.uri(b -> b.path(path).queryParam("serviceKey", "{key}").queryParam("returnType", "JSON").build(serviceKey))
                : spec.uri(base + path + "?serviceKey={key}&returnType=JSON", serviceKey);
        return request
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(Map.class);
    }

    private static Map<?, ?> firstRow(Map<?, ?> body) {
        Object data = body == null ? null : body.get("data");
        return data instanceof List<?> rows && !rows.isEmpty() && rows.getFirst() instanceof Map<?, ?> row ? row : null;
    }

    /** 상태조회 한 줄(진위확인 응답의 {@code status} 도 같은 칸) */
    static NtsLookup statusOf(Map<?, ?> row) {
        String taxType = text(row.get("tax_type"));
        LocalDate end = date(text(row.get("end_dt")));
        String tax = taxType.isEmpty() ? null : taxType;
        return switch (text(row.get("b_stt_cd"))) {
            case "01" -> new NtsLookup(NtsBizStatus.CONTINUING, tax, null, null);
            case "02" -> new NtsLookup(NtsBizStatus.PAUSED, tax, end, null);
            case "03" -> new NtsLookup(NtsBizStatus.CLOSED, tax, end, null);
            case "" -> new NtsLookup(NtsBizStatus.NOT_FOUND, null, null, null);
            default -> NtsLookup.unavailable("ERROR");
        };
    }

    /** 실패 → 사유 코드. 로그에는 예외 클래스 이름과 HTTP 상태 번호만 */
    private static String failure(String what, RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            log.warn("국세청 {} 응답 없음 — {}", what, e.getClass().getSimpleName());
            return "TIMEOUT";
        }
        if (e instanceof RestClientResponseException http) {
            int code = http.getStatusCode().value();
            log.warn("국세청 {} 실패 — HTTP {}", what, code);
            return code == HttpStatus.UNAUTHORIZED.value() || code == HttpStatus.FORBIDDEN.value() ? "KEY_INVALID" : "ERROR";
        }
        log.warn("국세청 {} 실패 — {}", what, e.getClass().getSimpleName());
        return "ERROR";
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().strip();
    }

    private static LocalDate date(String ymd) {
        if (ymd.length() != 8) {
            return null;
        }
        try {
            return LocalDate.parse(ymd, YMD);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
