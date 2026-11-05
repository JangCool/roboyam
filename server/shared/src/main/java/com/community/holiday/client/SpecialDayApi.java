package com.community.holiday.client;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공공데이터포털 「한국천문연구원_특일 정보」 공휴일 조회({@code getRestDeInfo}, data.go.kr 15012690) HTTP 호출 — 인증키·주소·요청 글자 틀·응답 칸 이름을 <b>호출마다 인자로</b> 받는다
 * (키는 공공데이터 설정의 일반 인증키 하나 — {@code PublicDataSettingsService#ntsServiceKey}, 주소 묶음은 {@code PublicDataEndpointService#holiday}).
 *
 * <ul>
 *   <li>요청: {@code GET <주소>?<요청 글자 틀>} — 기본 틀 {@value #DEFAULT_QUERY_TEMPLATE}. 자리 {@code {serviceKey}}·{@code {year}}·{@code {month}}(두 자리)·{@code {pageNo}}(늘 1)·
 *       {@code {numOfRows}}(늘 {@value #ROWS}) 만 바꾸고 나머지 글자는 그대로 보낸다. {@code solMonth} 가 필수라 한 해는 1~12월 열두 번 부른다.</li>
 *   <li>응답(JSON): {@code response.header.resultCode}({@code 00} 정상) · {@code response.body.items.item} — 여럿이면 배열, <b>하나면 객체, 없으면 {@code items} 가 빈 글자</b>.
 *       칸: 날짜 {@code locdate}(YYYYMMDD 숫자) · 이름 {@code dateName} · {@code isHoliday}(Y/N — N 은 거른다). 날짜·이름 칸 이름은 설정으로 바꿀 수 있다(뼈대는 고정).</li>
 *   <li>키 오류: HTTP 401·403, 결과 코드 20·30·31·32, 또는 XML 오류 응답의 {@code SERVICE_KEY_IS_NOT_REGISTERED}·{@code SERVICE_ACCESS_DENIED} — 사유 {@code KEY_INVALID}.
 *       응답 없음 {@code TIMEOUT}, 그 밖(읽을 수 없는 본문·다른 결과 코드) {@code ERROR}.</li>
 *   <li>인증키: 일반 인증키(Decoding)를 받는다. 이미 URL 인코딩된 키(Encoding — {@code %} 가 있음)는 풀어서 쓴다. {@code +}·{@code /}·{@code =} 가 깨지지 않게 키만 폼 인코딩해 이미
 *       인코딩된 주소로 보낸다(스프링이 다시 손대지 않게 {@link URI}).</li>
 * </ul>
 * 던지지 않는다. 키·주소의 쿼리는 로그에 싣지 않는다(실패는 해·달·예외 클래스 이름·HTTP 상태 번호만).
 */
public class SpecialDayApi {

    private static final Logger log = LoggerFactory.getLogger(SpecialDayApi.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    /** 공식 기본 주소(오퍼레이션 주소 전체) */
    public static final String DEFAULT_URL = "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";
    /** 기본 요청 글자 틀 */
    public static final String DEFAULT_QUERY_TEMPLATE = "serviceKey={serviceKey}&solYear={year}&solMonth={month}&pageNo={pageNo}&numOfRows={numOfRows}&_type=json";
    public static final String DEFAULT_DATE_FIELD = "locdate";
    public static final String DEFAULT_NAME_FIELD = "dateName";
    /** 한 달에 받는 줄 수(한 달 공휴일은 많아야 몇 줄 — 쪽 넘김 없이 한 번에) */
    static final int ROWS = 100;
    /** 이름 최대 길이(표 칸) */
    static final int NAME_MAX = 40;
    /** 키 오류 결과 코드 — 20 접근 거부 · 30 등록되지 않은 키 · 31 기한 만료 · 32 등록되지 않은 IP */
    private static final Set<String> KEY_CODES = Set.of("20", "30", "31", "32");

    /** 주소 묶음 — 빈 칸 없이(기본값을 채워서) 넘긴다 */
    public record Endpoint(String url, String queryTemplate, String dateField, String nameField) {

        public static Endpoint defaults() {
            return new Endpoint(DEFAULT_URL, DEFAULT_QUERY_TEMPLATE, DEFAULT_DATE_FIELD, DEFAULT_NAME_FIELD);
        }
    }

    /** 공휴일 한 줄 — 대체공휴일은 이름으로 안다(「대체공휴일」·「대체 공휴일」) */
    public record Item(LocalDate date, String name, boolean substitute) {
    }

    /**
     * 조회 결과 — 실패면 {@code failure}(KEY_INVALID · TIMEOUT · ERROR)가 있고 {@code items} 는 비어 있다.
     */
    public record Fetch(List<Item> items, String failure) {

        public boolean ok() {
            return failure == null;
        }

        static Fetch failed(String failure) {
            return new Fetch(List.of(), failure);
        }
    }

    private final RestClient client;

    public SpecialDayApi(RestClient client) {
        this.client = client;
    }

    /** 한 해(1~12월) — 한 달이라도 실패하면 그 해 전체가 실패다(부분 응답으로 표를 맞추지 않는다) */
    public Fetch year(String serviceKey, int year, Endpoint endpoint) {
        List<Item> all = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            Fetch one = month(serviceKey, year, month, endpoint);
            if (!one.ok()) {
                return one;
            }
            all.addAll(one.items());
        }
        return new Fetch(List.copyOf(all), null);
    }

    /** 한 달 — 연결 시험도 이것을 쓴다(올해 1월) */
    public Fetch month(String serviceKey, int year, int month, Endpoint endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint.url() + "?" + query(endpoint.queryTemplate(), year, month, URLEncoder.encode(normalizeKey(serviceKey), StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException e) {
            log.warn("특일 정보 주소를 만들 수 없다({}년 {}월) — 요청 글자 틀을 확인", year, month);
            return Fetch.failed("ERROR");
        }
        String body;
        try {
            body = client.get().uri(uri).accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML).retrieve().body(String.class);
        } catch (RuntimeException e) {
            return Fetch.failed(failure(year, month, e));
        }
        return parse(body, endpoint, year, month);
    }

    /** 요청 글자 틀의 자리를 바꾼다 — 키는 이미 인코딩한 값 */
    static String query(String template, int year, int month, String encodedKey) {
        return template.replace("{serviceKey}", encodedKey).replace("{year}", String.valueOf(year)).replace("{month}", "%02d".formatted(month))
                .replace("{pageNo}", "1").replace("{numOfRows}", String.valueOf(ROWS));
    }

    /** 이미 URL 인코딩된 키(Encoding 키 — {@code %} 가 있음)는 원래 글자로 */
    static String normalizeKey(String key) {
        if (key == null || !key.contains("%")) {
            return key == null ? "" : key;
        }
        try {
            return URLDecoder.decode(key, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return key;
        }
    }

    static Fetch parse(String body, Endpoint endpoint, int year, int month) {
        if (body == null || body.isBlank()) {
            return new Fetch(List.of(), null);
        }
        String trimmed = body.strip();
        if (trimmed.startsWith("<")) {
            boolean key = trimmed.contains("SERVICE_KEY_IS_NOT_REGISTERED") || trimmed.contains("SERVICE_ACCESS_DENIED") || trimmed.contains("DEADLINE_HAS_EXPIRED")
                    || trimmed.contains("UNREGISTERED_IP");
            log.warn("특일 정보 응답이 JSON 이 아니다({}년 {}월){}", year, month, key ? " — 키 오류" : "");
            return Fetch.failed(key ? "KEY_INVALID" : "ERROR");
        }
        JsonNode root;
        try {
            root = JSON.readTree(trimmed);
        } catch (JacksonException e) {
            log.warn("특일 정보 응답을 읽지 못했다({}년 {}월) — {}", year, month, e.getClass().getSimpleName());
            return Fetch.failed("ERROR");
        }
        JsonNode response = root.path("response");
        String code = text(response.path("header").path("resultCode"));
        if (response.isMissingNode() || !"00".equals(code)) {
            log.warn("특일 정보 결과 코드 {}({}년 {}월)", code.isEmpty() ? "없음" : code, year, month);
            return Fetch.failed(KEY_CODES.contains(code) ? "KEY_INVALID" : "ERROR");
        }
        JsonNode item = response.path("body").path("items").path("item");
        List<JsonNode> nodes = new ArrayList<>();
        if (item.isArray()) {
            item.forEach(nodes::add);
        } else if (item.isObject()) {
            nodes.add(item);
        }
        List<Item> out = new ArrayList<>();
        for (JsonNode n : nodes) {
            if ("N".equalsIgnoreCase(text(n.path("isHoliday")))) {
                continue;
            }
            LocalDate date = date(text(n.path(endpoint.dateField())));
            String name = text(n.path(endpoint.nameField()));
            if (date == null || name.isEmpty()) {
                log.warn("특일 정보 줄을 읽지 못해 건너뛴다({}년 {}월) — 날짜·이름 칸 이름을 확인", year, month);
                return Fetch.failed("ERROR");
            }
            String cut = name.length() > NAME_MAX ? name.substring(0, NAME_MAX).strip() : name;
            out.add(new Item(date, cut, cut.replace(" ", "").contains("대체공휴일")));
        }
        return new Fetch(List.copyOf(out), null);
    }

    private static String failure(int year, int month, RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            log.warn("특일 정보 응답 없음({}년 {}월) — {}", year, month, e.getClass().getSimpleName());
            return "TIMEOUT";
        }
        if (e instanceof RestClientResponseException http) {
            int code = http.getStatusCode().value();
            log.warn("특일 정보 실패({}년 {}월) — HTTP {}", year, month, code);
            return code == HttpStatus.UNAUTHORIZED.value() || code == HttpStatus.FORBIDDEN.value() ? "KEY_INVALID" : "ERROR";
        }
        log.warn("특일 정보 실패({}년 {}월) — {}", year, month, e.getClass().getSimpleName());
        return "ERROR";
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asString("").strip();
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
