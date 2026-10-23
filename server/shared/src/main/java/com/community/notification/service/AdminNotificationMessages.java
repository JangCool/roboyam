package com.community.notification.service;

import com.community.notification.entity.AdminNotificationEvent;
import com.community.notificationtemplate.catalog.MessageChannel;
import com.community.notificationtemplate.catalog.MessageTargetCatalog;
import com.community.notificationtemplate.catalog.MessageVariable;
import com.community.notificationtemplate.engine.MessageRenderRequest;
import com.community.notificationtemplate.engine.NotificationMessageRenderer;
import com.community.notificationtemplate.engine.RenderedInApp;
import com.community.zconfiguration.message.Messages;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 관리자 알림(운영자 이벤트)의 저장된 인자를 알림 문구 엔진의 요청으로 바꾼다 — 종 서랍(읽을 때) · 알림 메일 · Slack/Mattermost 게시가 같은 길을 쓴다.
 *
 * <p>알림 행은 예전처럼 <b>인자만</b>({@code admin_notifications.args} — 글자 · 수 · 읽을 때 풀리는 문구 참조) 저장하고, 문구는 <b>읽을 때·보낼 때 받는 사람 언어로</b> 그린다:
 * 인자를 그 언어로 푼 뒤({@link NotificationArgs#resolve}) 카탈로그가 정한 자리 이름({@code MessageTarget.InAppText#argNames} — 예: 배치 실패 {@code jobName}·{@code reason})의
 * 변수로 넘긴다. 그래서 운영자가 문구를 바꾸면 이미 쌓인 알림도 새 문구로 보이고, 읽는 사람마다 자기 언어의 문구를 본다.
 */
@Component
public class AdminNotificationMessages {

    private final NotificationMessageRenderer renderer;
    private final Messages messages;

    public AdminNotificationMessages(NotificationMessageRenderer renderer, Messages messages) {
        this.renderer = renderer;
        this.messages = messages;
    }

    /**
     * 이 이벤트·채널의 요청 — 받는 사람 언어와 인자 변수까지 채운 빌더(부르는 쪽이 받는 사람 이름·링크·조건·시각을 더한다).
     *
     * @param argsJson   알림 행의 인자(JSON 배열 글자, 없으면 null)
     * @param conditions 발행 지점이 채운 조건 값(없으면 빈 맵)
     */
    public MessageRenderRequest.Builder request(AdminNotificationEvent event, MessageChannel channel, String argsJson, Locale locale,
                                                Map<String, ? extends java.util.Collection<String>> conditions) {
        MessageRenderRequest.Builder builder = MessageRenderRequest.event(event, channel).locale(locale);
        List<String> names = MessageTargetCatalog.of(event).inApp().argNames();
        Object[] args = NotificationArgs.resolve(argsJson, messages, locale);
        for (int i = 0; i < names.size() && i < args.length; i++) {
            if (names.get(i) != null) {
                builder.variable(names.get(i), args[i]);
            }
        }
        if (conditions != null) {
            conditions.forEach((key, values) -> builder.condition(key, values.toArray(String[]::new)));
        }
        return builder;
    }

    /** 종 서랍 한 줄 — 읽는 사람 언어로 그린다({@code recipientName} = 읽는 운영자 이름) */
    public RenderedInApp inApp(AdminNotificationEvent event, String argsJson, Locale locale, String recipientName) {
        return renderer.renderInApp(request(event, MessageChannel.IN_APP, argsJson, locale, Map.<String, Set<String>>of())
                .variable(MessageVariable.RECIPIENT_NAME, recipientName).build());
    }
}
