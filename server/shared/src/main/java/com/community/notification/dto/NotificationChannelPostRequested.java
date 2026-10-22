package com.community.notification.dto;

import com.community.notification.entity.NotificationSeverity;
import java.util.List;

/**
 * 알림을 공유 채널(Slack · Mattermost)에 한 번 올려 달라는 요청 — 발행기가 업무 트랜잭션 안에서 알려 두고, 트랜잭션이 <b>커밋된 뒤에만</b> 발송기가 받는다(롤백되면 게시도 없다).
 * 도구의 설정·비밀은 싣지 않는다 — 발송기가 올릴 때 DB 에서 읽는다. 문구는 키 + 인자로 들고 와 사이트 표시 기본 언어로 푼다.
 *
 * @param channelIds 올릴 도구(발행 때 켜져 있던 것) — 이벤트당 한 번씩, 수신자 수와 상관없다
 * @param linkPath   인자를 채운 이동 경로(없으면 null)
 */
public record NotificationChannelPostRequested(String eventCode, NotificationSeverity severity, String titleKey, String bodyKey, String argsJson, String linkPath,
                                               List<Long> channelIds) {

    public NotificationChannelPostRequested {
        channelIds = List.copyOf(channelIds);
    }
}
