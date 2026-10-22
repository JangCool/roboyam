package com.community.mail.dto.response;

import java.util.List;

/** 메일 서버 설정 이력 한 쪽 — 최신이 위, {@code page} 는 0 부터 */
public record MailSettingHistoryPage(List<MailSettingHistoryItemView> items, long total, int page, int size) {
}
