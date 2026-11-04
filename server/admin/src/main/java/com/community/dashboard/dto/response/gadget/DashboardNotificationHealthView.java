package com.community.dashboard.dto.response.gadget;

import java.time.Instant;
import java.util.List;

/**
 * {@code NOTIFICATION_HEALTH} — 오늘(한국 날짜) 보낸 수 · 채널별(EMAIL · SLACK · MATTERMOST — {@code notification.channelType.*}) 보낸·실패 수 + 최근 실패 3건.
 * {@code summary} 는 사유 코드의 요청 언어 문구({@code notification.channel.test.<코드>}). 수신자·본문은 없다.
 */
public record DashboardNotificationHealthView(long sentToday, List<Channel> channels, List<RecentFailure> recentFailures) implements DashboardGadgetData {

    public record Channel(String channel, long sent, long failed) {
    }

    public record RecentFailure(String id, String channel, String summary, Instant failedAt) {
    }
}
