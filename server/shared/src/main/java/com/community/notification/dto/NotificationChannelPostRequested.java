package com.community.notification.dto;

import com.community.notification.entity.NotificationSeverity;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 알림을 공유 채널(Slack · Mattermost)에 한 번 올려 달라는 요청 — 발행기가 업무 트랜잭션 안에서 알려 두고, 트랜잭션이 <b>커밋된 뒤에만</b> 발송기가 받는다(롤백되면 게시도 없다).
 * 도구의 설정·비밀은 싣지 않는다 — 발송기가 올릴 때 DB 에서 읽는다. 문구는 알림 행과 같은 인자로 들고 와 사이트 표시 기본 언어로 알림 문구 엔진이 그린다.
 *
 * @param channelIds 올릴 도구(발행 때 켜져 있던 것) — 이벤트당 한 번씩, 수신자 수와 상관없다
 * @param linkPath   인자를 채운 이동 경로(없으면 null)
 * @param conditions 발행 지점이 채운 조건 값
 * @param occurredAt 사건 시각(변수 {@code occurredAt})
 */
public record NotificationChannelPostRequested(String eventCode, NotificationSeverity severity, String argsJson, String linkPath, List<Long> channelIds,
                                               Map<String, List<String>> conditions, Instant occurredAt) {

    public NotificationChannelPostRequested {
        channelIds = List.copyOf(channelIds);
        conditions = conditions == null ? Map.of() : Map.copyOf(conditions);
    }
}
