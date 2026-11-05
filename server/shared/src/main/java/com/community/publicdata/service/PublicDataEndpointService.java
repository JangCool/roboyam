package com.community.publicdata.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.business.nts.NtsProperties;
import com.community.holiday.client.SpecialDayApi;
import com.community.publicdata.dto.response.PublicDataEndpointsView;
import com.community.publicdata.dto.response.PublicDataSettingsView;
import com.community.publicdata.repository.master.PublicDataEndpointStore;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공공데이터 API 주소 설정(사용자 지시 2026-11-05 「받는 주소가 바뀔 수 있으니 설정 화면에 API 주소 칸」) — 국세청 사업자 상태 조회 기본 주소와 특일 정보(공휴일) 주소·요청 글자 틀·응답 칸 이름.
 *
 * <ul>
 *   <li>읽는 차례: 화면에서 넣은 값 → 기본값(국세청은 배포 설정 {@code roboyam.business.nts.base-url} — 기본 {@code https://api.odcloud.kr}, 특일 정보는 코드의 공식 값
 *       {@link SpecialDayApi#DEFAULT_URL} 등). 요청마다 DB 를 읽는다 — 저장하면 재시작 없이 다음 요청부터, 서버가 여럿이어도 같은 값.</li>
 *   <li>검사(400 {@code fields.<칸>}): 주소는 {@code https://} 만 · {@value #URL_MAX}자 이하 · 공백·{@code ?}·{@code #} 없음 · 끝의 {@code /} 는 지운다.
 *       요청 글자 틀은 {@value #TEMPLATE_MAX}자 이하 · 공백 없음 · {@code {serviceKey}}·{@code {year}}·{@code {month}} 자리 필수. 칸 이름은 영문·숫자·밑줄 {@value #FIELD_MAX}자 이하.
 *       빈 칸은 기본값으로 돌아간다.</li>
 *   <li>감사: 저장 {@code PUBLIC_DATA_ENDPOINT_UPDATE}(바뀐 칸 + 전후 값) · 되돌리기 {@code PUBLIC_DATA_ENDPOINT_RESET} — 대상 PUBLIC_DATA_SETTINGS/{@code nts}·{@code holiday}.</li>
 * </ul>
 * 권한 검사는 컨트롤러 선언이 한다(보기 {@code site.read}, 저장·되돌리기 {@code site.secret} — 인증키 저장과 같다).
 */
@Service
public class PublicDataEndpointService {

    /** API 이름(표의 {@code api} 칸 · 경로 · 감사 대상 ID 의 대문자) */
    public static final String NTS = "NTS";
    public static final String HOLIDAY = "HOLIDAY";
    static final int URL_MAX = 500;
    static final int TEMPLATE_MAX = 1000;
    static final int FIELD_MAX = 50;
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0," + (FIELD_MAX - 1) + "}");
    private static final List<String> REQUIRED_SLOTS = List.of("{serviceKey}", "{year}", "{month}");

    private final PublicDataEndpointStore store;
    private final NtsProperties nts;
    private final AuditLogService audit;
    private final ObjectProvider<Clock> clocks;

    public PublicDataEndpointService(PublicDataEndpointStore store, NtsProperties nts, AuditLogService audit, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.nts = nts;
        this.audit = audit;
        this.clocks = clocks;
    }

    /** 지금 쓸 국세청 기본 주소(끝 {@code /} 없음) */
    public String ntsBaseUrl() {
        String screen = store.find(NTS).map(PublicDataEndpointStore.Row::baseUrl).orElse(null);
        return screen != null ? screen : ntsDefault();
    }

    /** 지금 쓸 특일 정보 주소 묶음(빈 칸은 기본값으로 채움) */
    public SpecialDayApi.Endpoint holiday() {
        PublicDataEndpointStore.Row row = store.find(HOLIDAY).orElse(null);
        SpecialDayApi.Endpoint d = SpecialDayApi.Endpoint.defaults();
        if (row == null) {
            return d;
        }
        return new SpecialDayApi.Endpoint(or(row.baseUrl(), d.url()), or(row.queryTemplate(), d.queryTemplate()), or(row.dateField(), d.dateField()),
                or(row.nameField(), d.nameField()));
    }

    /** 화면 — 칸마다 지금 값·기본값·직접 넣었는가 + 마지막 변경 + 특일 정보 마지막 연결 시험 */
    public PublicDataEndpointsView view() {
        PublicDataEndpointStore.Row n = store.find(NTS).orElse(null);
        PublicDataEndpointStore.Row h = store.find(HOLIDAY).orElse(null);
        SpecialDayApi.Endpoint d = SpecialDayApi.Endpoint.defaults();
        PublicDataEndpointsView.Nts ntsView = new PublicDataEndpointsView.Nts(field(n == null ? null : n.baseUrl(), ntsDefault()), n == null ? null : n.updatedAt(),
                n == null ? null : n.updatedByName());
        PublicDataSettingsView.Test test = h == null || h.testedResult() == null ? null
                : new PublicDataSettingsView.Test(PublicDataSettingsView.Result.valueOf(h.testedResult()), h.testedAt(), h.testedByName());
        PublicDataEndpointsView.Holiday holidayView = new PublicDataEndpointsView.Holiday(field(h == null ? null : h.baseUrl(), d.url()),
                field(h == null ? null : h.queryTemplate(), d.queryTemplate()), field(h == null ? null : h.dateField(), d.dateField()),
                field(h == null ? null : h.nameField(), d.nameField()), h == null ? null : h.updatedAt(), h == null ? null : h.updatedByName(), test);
        return new PublicDataEndpointsView(ntsView, holidayView);
    }

    /** 국세청 주소 저장 — 빈 값이면 기본값 */
    @Transactional
    public PublicDataEndpointsView saveNts(String url, long operatorId, String ip) {
        String value = url(url);
        PublicDataEndpointStore.Row before = store.find(NTS).orElse(null);
        store.save(NTS, value, null, null, null, operatorId, now());
        auditSave(NTS, before, values(value, null, null, null), operatorId, ip);
        return view();
    }

    /** 특일 정보 주소 묶음 저장 — 빈 칸은 기본값 */
    @Transactional
    public PublicDataEndpointsView saveHoliday(String url, String queryTemplate, String dateField, String nameField, long operatorId, String ip) {
        String u = url(url);
        String q = template(queryTemplate);
        String df = fieldName("dateField", dateField);
        String nf = fieldName("nameField", nameField);
        PublicDataEndpointStore.Row before = store.find(HOLIDAY).orElse(null);
        store.save(HOLIDAY, u, q, df, nf, operatorId, now());
        auditSave(HOLIDAY, before, values(u, q, df, nf), operatorId, ip);
        return view();
    }

    /** 기본값으로 되돌리기(모든 칸) — 감사에 되돌리기 전 값 */
    @Transactional
    public PublicDataEndpointsView reset(String api, long operatorId, String ip) {
        PublicDataEndpointStore.Row before = store.find(api).orElse(null);
        store.save(api, null, null, null, null, operatorId, now());
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.PUBLIC_DATA_ENDPOINT_RESET, AuditResult.SUCCESS)
                .target(PublicDataSettingsService.AUDIT_TARGET, auditId(api)).with("before", values(before)));
        return view();
    }

    /** 특일 정보 연결 시험 결과를 남긴다 */
    @Transactional
    public void recordHolidayTest(String result, long operatorId) {
        store.recordTest(HOLIDAY, result, operatorId, now());
    }

    // ── 검사 ──

    /** 주소 — 빈 값은 null(기본값) */
    static String url(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.length() > URL_MAX) {
            throw ValidationException.of("url", new MessageRef("publicData.endpoint.url.max", URL_MAX));
        }
        if (value.chars().anyMatch(Character::isWhitespace) || value.contains("?") || value.contains("#")) {
            throw ValidationException.of("url", new MessageRef("publicData.endpoint.url.invalid"));
        }
        if (!value.regionMatches(true, 0, "https://", 0, 8)) {
            throw ValidationException.of("url", new MessageRef("publicData.endpoint.url.https"));
        }
        try {
            URI uri = new URI(value);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw ValidationException.of("url", new MessageRef("publicData.endpoint.url.invalid"));
            }
        } catch (URISyntaxException e) {
            throw ValidationException.of("url", new MessageRef("publicData.endpoint.url.invalid"));
        }
        return "https://" + value.substring(8);
    }

    static String template(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("?")) {
            value = value.substring(1);
        }
        if (value.length() > TEMPLATE_MAX) {
            throw ValidationException.of("queryTemplate", new MessageRef("publicData.endpoint.template.max", TEMPLATE_MAX));
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw ValidationException.of("queryTemplate", new MessageRef("publicData.endpoint.template.whitespace"));
        }
        if (!REQUIRED_SLOTS.stream().allMatch(value::contains)) {
            throw ValidationException.of("queryTemplate", new MessageRef("publicData.endpoint.template.slots"));
        }
        return value;
    }

    static String fieldName(String field, String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (!FIELD_NAME.matcher(value).matches()) {
            throw ValidationException.of(field, new MessageRef("publicData.endpoint.field.pattern", FIELD_MAX));
        }
        return value;
    }

    // ── 안 ──

    private String ntsDefault() {
        String base = nts.baseUrl() == null ? "" : nts.baseUrl().strip();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private void auditSave(String api, PublicDataEndpointStore.Row before, Map<String, String> after, long operatorId, String ip) {
        Map<String, String> old = values(before);
        Set<String> fields = new LinkedHashSet<>();
        Map<String, String> beforeChanged = new LinkedHashMap<>();
        Map<String, String> afterChanged = new LinkedHashMap<>();
        for (String k : after.keySet()) {
            if (!Objects.equals(old.get(k), after.get(k))) {
                fields.add(k);
                beforeChanged.put(k, old.get(k));
                afterChanged.put(k, after.get(k));
            }
        }
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.PUBLIC_DATA_ENDPOINT_UPDATE, AuditResult.SUCCESS)
                .target(PublicDataSettingsService.AUDIT_TARGET, auditId(api)).with("fields", new ArrayList<>(fields)).with("before", beforeChanged).with("after", afterChanged));
    }

    private static Map<String, String> values(PublicDataEndpointStore.Row row) {
        return row == null ? values(null, null, null, null) : values(row.baseUrl(), row.queryTemplate(), row.dateField(), row.nameField());
    }

    /** 감사 칸(null = 기본값) — 국세청은 주소 칸만 */
    private static Map<String, String> values(String url, String template, String dateField, String nameField) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("url", url);
        m.put("queryTemplate", template);
        m.put("dateField", dateField);
        m.put("nameField", nameField);
        return m;
    }

    private static String auditId(String api) {
        return api.toLowerCase(java.util.Locale.ROOT);
    }

    private static PublicDataEndpointsView.Field field(String saved, String defaultValue) {
        return new PublicDataEndpointsView.Field(saved != null ? saved : defaultValue, defaultValue, saved != null);
    }

    private static String or(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant().truncatedTo(ChronoUnit.MICROS);
    }
}
