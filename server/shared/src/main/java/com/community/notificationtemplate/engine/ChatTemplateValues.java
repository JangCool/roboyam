package com.community.notificationtemplate.engine;

import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.MessageSeverity;
import java.time.Instant;
import java.util.Map;

/**
 * Slack · Mattermost 템플릿(메시지 카드의 겉모양) 하나의 값 — 기본 것(코드)과 사용자 것(DB)이 같은 모양이다.
 *
 * @param id     기본 것은 {@code slack-default} · {@code mattermost-default}, 사용자 것은 Snowflake 글자
 * @param tool   {@link MessageChannel#SLACK} · {@link MessageChannel#MATTERMOST}
 * @param colors 심각도별 색 막대
 * @param frames 언어별 본문 틀(그 앱 표기 + {@code {{content}}} 정확히 한 번)
 */
public record ChatTemplateValues(String id, MessageChannel tool, String name, boolean builtin, boolean showSeverityIcon, boolean boldTitle,
                                 Map<MessageSeverity, ChatColor> colors, boolean showSiteName, boolean showSentAt, boolean showOpenButton,
                                 Map<String, String> openLabel, Map<String, String> frames, Instant updatedAt, Long updatedBy) {

    public ChatTemplateValues {
        colors = Map.copyOf(colors);
        openLabel = Map.copyOf(openLabel);
        frames = Map.copyOf(frames);
    }

    public ChatColor color(MessageSeverity severity) {
        ChatColor color = colors.get(severity == null ? MessageSeverity.INFO : severity);
        return color == null ? ChatColor.BLUE : color;
    }

    public String openLabel(String locale) {
        return openLabel.getOrDefault(locale, openLabel.getOrDefault(MessageLocales.KO, ""));
    }

    public String frame(String locale) {
        return frames.getOrDefault(locale, frames.getOrDefault(MessageLocales.KO, "{{content}}"));
    }
}
