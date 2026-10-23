package com.community.notificationtemplate.dto.response;

import java.util.List;

/**
 * {@code GET /api/admin/notification-messages/targets} 의 한 줄 — 대상(이벤트 · 필수 안내 메일) 하나와 채널별 요약.
 *
 * @param type     {@code EVENT} · {@code REQUIRED_MAIL}
 * @param audience {@code MEMBER} · {@code OPERATOR}
 * @param area     이벤트의 영역(알림 화면과 같은 값) — 필수 안내 메일은 null
 * @param severity 운영자 이벤트의 심각도({@code INFO|WARNING|CRITICAL}) — 그 밖은 null
 * @param name     요청 언어의 이름
 * @param security 보안 메일(필수 변수를 지우면 저장 안 됨)
 * @param channels 이 대상이 나가는 채널(차례 {@code IN_APP · EMAIL · SLACK · MATTERMOST · KAKAO})
 */
public record MessageTargetView(String type, String code, String audience, String area, String severity, String name, String description, boolean security,
                                List<MessageChannelSummaryView> channels) {
}
