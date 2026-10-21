package com.community.site.service;

import com.community.site.dto.request.DisplayValuesRequest;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 표시 형식(언어·시간대·시간 형식·날짜 형식) 네 칸의 허용 값과 검사 — 사이트 표시 기본값({@link SiteDisplayService})과 내 표시 형식(내 정보)이 같은 규칙을 쓴다.
 * 허용 값은 공유 프론트 레이어({@code apps-packages/display-time})와 같다: 언어 {@code ko}·{@code en}, 시간 형식 {@code H24}·{@code H12},
 * 날짜 형식 {@code YMD_DASH}·{@code YMD_DOT}·{@code DMY_SLASH}·{@code MDY_SLASH}, 시간대는 IANA 이름(64자 이하, JDK 가 아는 이름).
 * 위반은 칸마다 한꺼번에 돌려준다({@code fields.language|zoneId|timeFormat|dateFormat}).
 */
public final class DisplayValuesPolicy {

    private static final Set<String> LANGUAGES = Set.of("ko", "en");
    private static final Set<String> TIME_FORMATS = Set.of("H24", "H12");
    private static final Set<String> DATE_FORMATS = Set.of("YMD_DASH", "YMD_DOT", "DMY_SLASH", "MDY_SLASH");
    private static final int ZONE_MAX_LENGTH = 64;

    /** 검사를 마치고 앞뒤 공백을 자른 네 칸 */
    public record Checked(String language, String zoneId, String timeFormat, String dateFormat) {
    }

    private DisplayValuesPolicy() {
    }

    /**
     * @param values 요청의 네 칸. null 이면 「보내 주세요」(language 칸에 붙인다)
     * @throws ValidationException 하나라도 어긋났을 때(칸마다 문구)
     */
    public static Checked check(DisplayValuesRequest values) {
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        if (values == null) {
            errors.put("language", new MessageRef("siteSetting.display.required"));
            throw new ValidationException(errors);
        }
        String language = strip(values.language());
        String zoneId = strip(values.zoneId());
        String timeFormat = strip(values.timeFormat());
        String dateFormat = strip(values.dateFormat());
        if (language == null || !LANGUAGES.contains(language)) {
            errors.put("language", new MessageRef("siteSetting.display.language"));
        }
        if (!validZone(zoneId)) {
            errors.put("zoneId", new MessageRef("siteSetting.display.zoneId"));
        }
        if (timeFormat == null || !TIME_FORMATS.contains(timeFormat)) {
            errors.put("timeFormat", new MessageRef("siteSetting.display.timeFormat"));
        }
        if (dateFormat == null || !DATE_FORMATS.contains(dateFormat)) {
            errors.put("dateFormat", new MessageRef("siteSetting.display.dateFormat"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Checked(language, zoneId, timeFormat, dateFormat);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static boolean validZone(String zoneId) {
        return zoneId != null && !zoneId.isEmpty() && zoneId.length() <= ZONE_MAX_LENGTH && ZoneId.getAvailableZoneIds().contains(zoneId);
    }
}
