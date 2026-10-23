package com.community.notificationtemplate.dto.response;

import java.util.List;

/** {@code GET /api/admin/notification-messages/targets/{type}/{code}} — 대상 정의(목록 한 줄과 같은 모양)와 채널마다의 쓸 문구·규칙·문구 목록 */
public record MessageTargetDetailView(MessageTargetView summary, List<MessageChannelDetailView> channels) {
}
