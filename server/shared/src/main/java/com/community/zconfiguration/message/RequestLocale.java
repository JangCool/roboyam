package com.community.zconfiguration.message;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 요청의 언어를 {@code Accept-Language} 헤더에서 정하는 한 곳. 지원 언어는 {@code ko}·{@code en} 이고, 헤더가 없거나 모르는 값이면 {@code ko} 다.
 *
 * <p>스프링 MVC 의 로캘 해석({@link RequestLocaleResolver})과 보안 필터(설치 가드·401·403 응답)가 <b>같은 규칙</b>을 쓰도록 헤더를 직접 읽는다 —
 * 필터는 DispatcherServlet 보다 먼저 돌아 스프링의 로캘 해석을 기다릴 수 없다. 톰캣의 {@code request.getLocale()} 은 헤더가 없으면
 * 서버 기본 로캘을 주므로 쓰지 않는다.
 */
public final class RequestLocale {

    public static final Locale KO = Locale.KOREAN;
    public static final Locale EN = Locale.ENGLISH;

    private RequestLocale() {
    }

    /** 이 요청의 언어({@link #KO} 또는 {@link #EN}) */
    public static Locale of(HttpServletRequest request) {
        return request == null ? KO : parse(request.getHeader("Accept-Language"));
    }

    /** {@code Accept-Language} 값에서 가중치(q)가 높은 순으로 처음 지원하는 언어를 고른다. 없으면 {@link #KO} */
    public static Locale parse(String header) {
        if (header == null || header.isBlank()) {
            return KO;
        }
        List<Locale.LanguageRange> ranges;
        try {
            ranges = new ArrayList<>(Locale.LanguageRange.parse(header));
        } catch (IllegalArgumentException e) {
            return KO; // 읽을 수 없는 헤더
        }
        ranges.sort(Comparator.comparingDouble(Locale.LanguageRange::getWeight).reversed());
        for (Locale.LanguageRange range : ranges) {
            if (range.getWeight() <= 0) {
                continue;
            }
            String tag = range.getRange();
            int dash = tag.indexOf('-');
            String language = dash < 0 ? tag : tag.substring(0, dash);
            if (language.equals("ko")) {
                return KO;
            }
            if (language.equals("en")) {
                return EN;
            }
            if (language.equals("*")) {
                return KO;
            }
        }
        return KO;
    }

    /** 지원하는 언어로 맞춘다 — {@code ko}·{@code en} 이 아니면 {@link #KO} */
    public static Locale normalize(Locale locale) {
        return locale != null && locale.getLanguage().equals("en") ? EN : KO;
    }
}
