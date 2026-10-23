package com.community.notificationtemplate.engine;

import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.NotificationSeverity;
import com.community.membernotification.entity.MemberEvent;
import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.MessageSeverity;
import com.community.notificationtemplate.catalog.MessageTargetType;
import com.community.notificationtemplate.catalog.RequiredMail;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 렌더링 엔진에 넘기는 한 번의 요청 — 무엇을(대상 · 채널) 누구 언어로 어떤 값·상황으로 그릴지. 빌더로 만든다:
 * <pre>{@code
 * MessageRenderRequest request = MessageRenderRequest.event(AdminNotificationEvent.BATCH_RUN_FAILED, MessageChannel.EMAIL)
 *         .locale(recipientLocale)                       // 받는 사람 언어(ko·en 밖이면 사이트 기본 언어)
 *         .variable("recipientName", operator.getOperatorName())
 *         .variable("jobName", jobDisplayName).variable("reason", errorSummary)
 *         .variable("link", adminUrl + "/settings/batch-logs?jobName=" + jobKey)
 *         .condition(MessageCondition.JOB_NAME, jobKey)       // 특수 상황 규칙의 조건 값
 *         .condition(MessageCondition.RECIPIENT_SOURCE, MessageCondition.DEFAULT)
 *         .occurredAt(run.getFinishedAt())
 *         .build();
 * }</pre>
 *
 * @param variables  변수 이름 → <b>가리기 전</b> 값(엔진이 채널에 맞게 가린다). 공통 변수 {@code siteName}·{@code siteUrl}·{@code adminUrl} 은 엔진이 채운다(주면 그 값).
 *                   이메일·Slack·Mattermost 의 {@code title}·{@code body} 를 주지 않으면 엔진이 같은 요청으로 그 대상의 앱 알림 문구를 먼저 그려 채운다.
 * @param conditions 조건 이름 → 그 상황의 값들(여럿이면 하나라도 맞으면 규칙이 맞는다)
 * @param severity   Slack · Mattermost 색 막대·그림(없으면 대상의 기본 심각도)
 * @param occurredAt {@code occurredAt} 변수를 주지 않았을 때 엔진이 {@code zone}(없으면 사이트 표시 시간대)으로 적을 시각
 */
public record MessageRenderRequest(MessageTargetType targetType, String targetCode, MessageChannel channel, String locale, Map<String, String> variables,
                                   Map<String, Set<String>> conditions, MessageSeverity severity, Instant occurredAt, ZoneId zone) {

    public MessageRenderRequest {
        variables = variables == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        conditions = conditions == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(conditions));
    }

    /** 운영자 이벤트 */
    public static Builder event(AdminNotificationEvent event, MessageChannel channel) {
        return new Builder(MessageTargetType.EVENT, event.code(), channel);
    }

    /** 회원 이벤트(코드는 통합 회원 이벤트 카탈로그와 같은 이름 — 지금은 {@link MemberEvent}) */
    public static Builder memberEvent(MemberEvent event, MessageChannel channel) {
        return new Builder(MessageTargetType.EVENT, event.code(), channel);
    }

    /** 필수 안내 메일(채널은 이메일뿐) */
    public static Builder requiredMail(RequiredMail mail) {
        return new Builder(MessageTargetType.REQUIRED_MAIL, mail.code(), MessageChannel.EMAIL);
    }

    /** 대상 종류 · 코드를 직접 줄 때 */
    public static Builder of(MessageTargetType type, String code, MessageChannel channel) {
        return new Builder(type, code, channel);
    }

    /** 채널만 바꾼 같은 요청(엔진이 {@code title}·{@code body} 를 위해 앱 알림을 먼저 그릴 때) */
    public MessageRenderRequest withChannel(MessageChannel other) {
        return new MessageRenderRequest(targetType, targetCode, other, locale, variables, conditions, severity, occurredAt, zone);
    }

    public static final class Builder {

        private final MessageTargetType type;
        private final String code;
        private final MessageChannel channel;
        private String locale;
        private final Map<String, String> variables = new LinkedHashMap<>();
        private final Map<String, Set<String>> conditions = new LinkedHashMap<>();
        private MessageSeverity severity;
        private Instant occurredAt;
        private ZoneId zone;

        private Builder(MessageTargetType type, String code, MessageChannel channel) {
            this.type = type;
            this.code = code;
            this.channel = channel;
        }

        /** 받는 사람 언어 — ko·en 밖이거나 null 이면 엔진이 사이트 표시 기본 언어(없으면 ko)로 본다 */
        public Builder locale(Locale value) {
            this.locale = MessageLocales.of(value);
            return this;
        }

        public Builder locale(String value) {
            this.locale = MessageLocales.of(value);
            return this;
        }

        /** 변수 값(가리기 전). null 은 빈 글자로 본다 */
        public Builder variable(String name, Object value) {
            variables.put(name, value == null ? "" : String.valueOf(value));
            return this;
        }

        public Builder variables(Map<String, ?> values) {
            values.forEach(this::variable);
            return this;
        }

        /** 그 상황의 조건 값 — 같은 이름을 여러 번 부르면 값이 쌓인다 */
        public Builder condition(String key, String... values) {
            conditions.computeIfAbsent(key, k -> new LinkedHashSet<>()).addAll(Arrays.asList(values));
            return this;
        }

        public Builder severity(MessageSeverity value) {
            this.severity = value;
            return this;
        }

        public Builder severity(NotificationSeverity value) {
            this.severity = value == null ? null : MessageSeverity.of(value);
            return this;
        }

        public Builder occurredAt(Instant value) {
            this.occurredAt = value;
            return this;
        }

        public Builder zone(ZoneId value) {
            this.zone = value;
            return this;
        }

        public MessageRenderRequest build() {
            Map<String, Set<String>> frozen = new LinkedHashMap<>();
            conditions.forEach((key, values) -> frozen.put(key, Set.copyOf(values)));
            return new MessageRenderRequest(type, code, channel, locale, variables, frozen, severity, occurredAt, zone);
        }
    }
}
