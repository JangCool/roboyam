package com.community.notificationtemplate.engine;

import com.community.notificationtemplate.catalog.MailTemplateAudience;
import com.community.notificationtemplate.catalog.MessageLocales;
import java.time.Instant;
import java.util.Map;

/**
 * 메일 템플릿(메일 한 통의 겉모양 틀) 하나의 값 — 기본 것(코드)과 사용자 것(DB)이 같은 모양이다. 렌더링 엔진과 관리 API 가 쓴다.
 *
 * @param id          기본 것은 {@code mail-member} · {@code mail-operator}, 사용자 것은 Snowflake 글자
 * @param name        이름 — 기본 것은 그 값을 만든 언어로 푼 이름
 * @param headerColor 머리말 띠 색 — null 이면 띠 없음
 * @param footer      언어별 꼬리말(평문 + 공통 변수)
 * @param frames      언어별 틀(간단 서식 HTML, {@code {{content}}} 정확히 한 번)
 */
public record MailTemplateValues(String id, String name, boolean builtin, MailTemplateAudience audience, boolean showLogo, boolean showSiteName,
                                 MailColor headerColor, Map<String, String> footer, Map<String, String> frames, Instant updatedAt, Long updatedBy) {

    public MailTemplateValues {
        footer = Map.copyOf(footer);
        frames = Map.copyOf(frames);
    }

    public String footer(String locale) {
        return footer.getOrDefault(locale, footer.getOrDefault(MessageLocales.KO, ""));
    }

    public String frame(String locale) {
        return frames.getOrDefault(locale, frames.getOrDefault(MessageLocales.KO, "<p>{{content}}</p>"));
    }
}
