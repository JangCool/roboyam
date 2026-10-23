package com.community.notificationtemplate.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 문구 · 템플릿 변경 이력 한 줄(설정 이력 {@code MailSettingHistoryItemView} 와 같은 모양).
 *
 * @param action       {@code CREATE} · {@code UPDATE}
 * @param source       늘 {@code ADMIN}
 * @param operatorName 바꾼 운영자 이름 — 지워졌으면 대체 문구(「삭제된 운영자」, 요청 언어)
 */
public record TemplateHistoryItemView(String id, String action, String source, String operatorId, String operatorName, boolean operatorDeleted, Instant changedAt,
                                      List<TemplateHistoryChangeView> changes) {
}
