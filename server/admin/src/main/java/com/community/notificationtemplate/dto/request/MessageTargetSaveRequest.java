package com.community.notificationtemplate.dto.request;

import java.util.Map;

/** {@code PUT /api/admin/notification-messages/targets/{type}/{code}} — 채널 이름 → 그 채널의 저장 값(보낸 채널만 바뀐다 — 준비 중 채널은 보내지 않는다) */
public record MessageTargetSaveRequest(Map<String, MessageChannelSaveRequest> channels) {
}
