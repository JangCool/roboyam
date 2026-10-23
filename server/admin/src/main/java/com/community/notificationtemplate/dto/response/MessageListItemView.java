package com.community.notificationtemplate.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/admin/notification-messages/messages} 의 한 줄 — 모든 대상·채널의 문구(본문 없이). 거르기·검색은 화면이 한다.
 *
 * @param targetName   요청 언어의 대상 이름
 * @param targetArea   대상의 영역(이벤트) — 필수 안내 메일은 null
 * @param preparing    준비 중인 채널의 문구(카카오톡)
 * @param templateName 템플릿 이름(앱 알림은 null)
 * @param defaultFor   이 문구를 「쓸 문구」로 고른 언어(저장된 상태)
 * @param ruleCount    이 문구를 쓰는 특수 상황 규칙 수
 */
public record MessageListItemView(String id, MessageTargetRefView target, String targetName, String targetArea, boolean security, String channel, boolean preparing,
                                  String name, String locale, String templateId, String templateName, boolean builtin, Instant updatedAt, String updatedByName,
                                  List<String> defaultFor, int ruleCount) {
}
