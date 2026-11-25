package com.community.groupbuy.tracking;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 스마트택배(스윗트래커) 조회 API — 공식 swagger {@code https://info.sweettracker.co.kr/v2/api-docs} 기준(계약 결정 90).
 * <ul>
 *   <li>{@code POST /api/v1/trackingInfo} 본문 JSON {@code {t_key, t_code, t_invoice}} → {@code TrackingInfo}({@code level}·{@code complete}·{@code result}·{@code trackingDetails[]}).
 *       GET 은 2026-12-01 에 끝나므로 POST 만 쓴다. TLS 1.2 이상은 자바 기본 HTTP 클라이언트가 맞춘다.</li>
 *   <li>{@code POST /api/v1/key/usage} 본문 {@code {t_key}} → {@code {key, startDate, endDate, totalAmount, leftAmount}}.</li>
 *   <li>오류는 HTTP 상태가 아니라 본문 {@code {status:false, code, msg}} 로 올 수도 있다 — 둘 다 본다. 101·102·103 키 문제 · 104 운송장/택배사 코드 · 105 하루 제한 · 106 조회 오류.</li>
 * </ul>
 * 던지지 않는다. 로그에는 키·응답 본문·기사 이름·전화를 싣지 않는다(송장은 끝 4자리, 실패는 코드·예외 클래스 이름만). 기사 이름·전화({@code manName}·{@code telno}·{@code telno2})는
 * 읽지도 않는다.
 */
public class SweetTrackerClient implements ParcelTrackingClient {

    private static final Logger log = LoggerFactory.getLogger(SweetTrackerClient.class);

    static final String TRACKING_PATH = "/api/v1/trackingInfo";
    static final String USAGE_PATH = "/api/v1/key/usage";
    /** 키 문제 코드 */
    static final Set<String> KEY_CODES = Set.of("101", "102", "103");
    /** 이력 한 줄 글자 수 상한 · 이력 줄 수 상한(최근 것) */
    static final int TEXT_MAX = 100;
    static final int EVENTS_MAX = 50;
    private static final java.time.ZoneId KST = java.time.ZoneId.of("Asia/Seoul");
    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"), DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"), DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));

    private final RestClient client;

    public SweetTrackerClient(RestClient client) {
        this.client = client;
    }

    @Override
    public Tracking track(String apiKey, String carrierCode, String invoiceNo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("t_key", apiKey);
        body.put("t_code", carrierCode);
        body.put("t_invoice", invoiceNo);
        try {
            Raw raw = post(TRACKING_PATH, body);
            Tracking result = parseTracking(raw.status(), raw.body());
            if (result.outcome() != Outcome.OK && result.outcome() != Outcome.NO_SCAN) {
                log.warn("스마트택배 조회 실패 — 택배사 {} 송장 {} 코드 {}", carrierCode, ParcelTrackingClient.masked(invoiceNo), result.errorCode());
            }
            return result;
        } catch (RuntimeException e) {
            return Tracking.failed(Outcome.FAILED, failure("조회", e, invoiceNo));
        }
    }

    @Override
    public Usage usage(String apiKey) {
        try {
            Raw raw = post(USAGE_PATH, Map.of("t_key", apiKey));
            return parseUsage(raw.status(), raw.body());
        } catch (RuntimeException e) {
            return Usage.failed(Outcome.FAILED, failure("이용권 조회", e, null));
        }
    }

    private record Raw(int status, Map<?, ?> body) {
    }

    private Raw post(String path, Object body) {
        return client.post().uri(path).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    Map<?, ?> parsed;
                    try {
                        parsed = response.bodyTo(Map.class);
                    } catch (RuntimeException e) {
                        parsed = null; // HTML 오류 쪽 등 — 상태 번호로 판정
                    }
                    return new Raw(status, parsed);
                });
    }

    /** 조회 응답 → 결과(시험이 직접 부른다) */
    static Tracking parseTracking(int httpStatus, Map<?, ?> body) {
        String code = errorCode(body);
        if (code != null) {
            return Tracking.failed(outcomeOf(code), code);
        }
        if (body == null || httpStatus >= 300) {
            return Tracking.failed(Outcome.FAILED, httpStatus >= 300 ? "HTTP_" + httpStatus : "ERROR");
        }
        List<Event> events = new ArrayList<>();
        if (body.get("trackingDetails") instanceof List<?> details) {
            for (Object d : details) {
                if (d instanceof Map<?, ?> m) {
                    Event e = event(m);
                    if (e != null) {
                        events.add(e);
                    }
                }
            }
        }
        events.sort(Comparator.comparing(Event::time));
        if (events.size() > EVENTS_MAX) {
            events = new ArrayList<>(events.subList(events.size() - EVENTS_MAX, events.size()));
        }
        Integer level = integer(body.get("level"));
        boolean complete = Boolean.TRUE.equals(body.get("complete")) || "Y".equalsIgnoreCase(text(body.get("completeYN"))) || (level != null && level == 6);
        if (complete && (level == null || level < 6)) {
            level = 6;
        }
        if (level == null) {
            level = events.isEmpty() ? 0 : events.getLast().level();
        }
        level = Math.max(0, Math.min(6, level));
        if (level == 0 && !complete) {
            return new Tracking(Outcome.NO_SCAN, "NO_SCAN", 0, false, List.copyOf(events), null);
        }
        Instant completedAt = null;
        if (complete) {
            for (Event e : events) {
                if (e.level() == 6) {
                    completedAt = e.time(); // 마지막 완료 줄
                }
            }
            if (completedAt == null && body.get("lastDetail") instanceof Map<?, ?> last && integer(last.get("level")) != null && integer(last.get("level")) == 6) {
                Event e = event(last);
                completedAt = e == null ? null : e.time();
            }
        }
        return new Tracking(Outcome.OK, null, level, complete, List.copyOf(events), completedAt);
    }

    static Usage parseUsage(int httpStatus, Map<?, ?> body) {
        String code = errorCode(body);
        if (code != null) {
            return Usage.failed(outcomeOf(code), code);
        }
        if (body == null || httpStatus >= 300) {
            return Usage.failed(Outcome.FAILED, httpStatus >= 300 ? "HTTP_" + httpStatus : "ERROR");
        }
        return new Usage(Outcome.OK, null, emptyToNull(text(body.get("startDate"))), emptyToNull(text(body.get("endDate"))), longValue(body.get("totalAmount")),
                longValue(body.get("leftAmount")));
    }

    /** 본문의 오류 코드({@code status:false} 이거나 {@code code} 가 있고 정상 칸이 없을 때) — 없으면 null */
    private static String errorCode(Map<?, ?> body) {
        if (body == null) {
            return null;
        }
        Object status = body.get("status");
        String code = text(body.get("code"));
        boolean failed = Boolean.FALSE.equals(status) || "false".equalsIgnoreCase(text(status));
        if (failed) {
            return code.isEmpty() ? "ERROR" : code;
        }
        if (!code.isEmpty() && body.get("level") == null && body.get("trackingDetails") == null && body.get("leftAmount") == null) {
            return code;
        }
        return null;
    }

    private static Outcome outcomeOf(String code) {
        if (KEY_CODES.contains(code)) {
            return Outcome.KEY_ERROR;
        }
        return "105".equals(code) ? Outcome.DAILY_LIMIT : Outcome.FAILED;
    }

    /** 이력 한 줄 — 시각·진행 상태 글·위치·단계만(기사 이름·전화는 읽지 않는다). 시각이 없으면 버린다 */
    private static Event event(Map<?, ?> m) {
        Instant time = time(text(m.get("timeString")), m.get("time"));
        if (time == null) {
            return null;
        }
        Integer level = integer(m.get("level"));
        return new Event(time, cut(text(m.get("kind"))), cut(text(m.get("where"))), level == null ? 0 : Math.max(0, Math.min(6, level)));
    }

    /** {@code timeString}(KST {@code yyyy-MM-dd HH:mm:ss}) → 순간, 읽지 못하면 {@code time}(epoch ms) */
    static Instant time(String timeString, Object epochMillis) {
        if (!timeString.isEmpty()) {
            for (DateTimeFormatter f : TIME_FORMATS) {
                try {
                    return LocalDateTime.parse(timeString, f).atZone(KST).toInstant();
                } catch (DateTimeParseException ignored) {
                    // 다음 모양
                }
            }
        }
        Long ms = longValue(epochMillis);
        return ms == null || ms <= 0 ? null : Instant.ofEpochMilli(ms);
    }

    private static String failure(String what, RuntimeException e, String invoiceNo) {
        String code = e instanceof ResourceAccessException ? "TIMEOUT" : "ERROR";
        log.warn("스마트택배 {} 실패 — 송장 {} {} ({})", what, invoiceNo == null ? "-" : ParcelTrackingClient.masked(invoiceNo), code, e.getClass().getSimpleName());
        return code;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().strip();
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static String cut(String value) {
        String v = value.replaceAll("\\s+", " ");
        return v.length() > TEXT_MAX ? v.substring(0, TEXT_MAX) : v;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            String t = text(value);
            return t.isEmpty() ? null : Integer.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longValue(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            String t = text(value);
            return t.isEmpty() ? null : Long.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
