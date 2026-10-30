package com.community.business.nts;

import com.community.business.entity.NtsBizStatus;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 공공데이터포털 「국세청 사업자등록정보 진위확인 및 상태조회」의 상태조회({@code POST /api/nts-businessman/v1/status?serviceKey=}, 본문 {@code {"b_no":["…"]}}).
 * 응답 {@code data[0].b_stt_cd}: {@code 01} 계속 · {@code 02} 휴업 · {@code 03} 폐업 · 빈 값이면 국세청에 등록되지 않은 번호. {@code end_dt}(YYYYMMDD)는 휴·폐업 일자.
 * 키·응답 본문·등록번호는 로그에 싣지 않는다(실패는 예외 클래스 이름만).
 */
public class OdcloudNtsStatusClient implements NtsStatusClient {

    private static final Logger log = LoggerFactory.getLogger(OdcloudNtsStatusClient.class);
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    private final String serviceKey;
    private final RestClient client;

    public OdcloudNtsStatusClient(String serviceKey, RestClient client) {
        this.serviceKey = serviceKey;
        this.client = client;
    }

    @Override
    public NtsProviderKind kind() {
        return NtsProviderKind.ODCLOUD;
    }

    @Override
    public NtsLookup lookup(String bizNo) {
        if (serviceKey == null || serviceKey.isBlank()) {
            return NtsLookup.unavailable("NO_KEY");
        }
        try {
            Map<?, ?> body = client.post()
                    .uri(b -> b.path("/api/nts-businessman/v1/status").queryParam("serviceKey", "{key}").queryParam("returnType", "JSON").build(serviceKey))
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(Map.of("b_no", List.of(bizNo)))
                    .retrieve().body(Map.class);
            Object data = body == null ? null : body.get("data");
            if (!(data instanceof List<?> rows) || rows.isEmpty() || !(rows.getFirst() instanceof Map<?, ?> row)) {
                return NtsLookup.unavailable("ERROR");
            }
            String code = text(row.get("b_stt_cd"));
            String taxType = text(row.get("tax_type"));
            LocalDate end = date(text(row.get("end_dt")));
            return switch (code) {
                case "01" -> new NtsLookup(NtsBizStatus.CONTINUING, taxType, null, null);
                case "02" -> new NtsLookup(NtsBizStatus.PAUSED, taxType, end, null);
                case "03" -> new NtsLookup(NtsBizStatus.CLOSED, taxType, end, null);
                case "" -> new NtsLookup(NtsBizStatus.NOT_FOUND, null, null, null);
                default -> NtsLookup.unavailable("ERROR");
            };
        } catch (ResourceAccessException e) {
            log.warn("국세청 상태조회 응답 없음 — {}", e.getClass().getSimpleName());
            return NtsLookup.unavailable("TIMEOUT");
        } catch (RuntimeException e) {
            log.warn("국세청 상태조회 실패 — {}", e.getClass().getSimpleName());
            return NtsLookup.unavailable("ERROR");
        }
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
