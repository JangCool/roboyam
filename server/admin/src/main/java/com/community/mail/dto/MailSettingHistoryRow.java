package com.community.mail.dto;

import com.community.mail.entity.MailSettingHistoryEntity;

/** 이력 한 줄 + 바꾼 운영자의 지금 이름(지워졌으면 null) — 조회용 */
public record MailSettingHistoryRow(MailSettingHistoryEntity history, String operatorName) {
}
