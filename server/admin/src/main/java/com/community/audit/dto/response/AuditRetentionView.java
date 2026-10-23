package com.community.audit.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 감사 보관 기간 — 분류 차례 고정(일반 운영 · 개인정보 열람 · 돈 관련).
 *
 * @param canEdit   이 운영자가 바꿀 수 있는가(최고 관리자만)
 * @param updatedAt 마지막 저장 시각(저장한 적 없으면 null)
 * @param updatedBy 마지막으로 바꾼 운영자 이름(없거나 지워졌으면 null)
 */
public record AuditRetentionView(List<Item> items, boolean canEdit, Instant updatedAt, String updatedBy) {

    /** @param days 정리 배치가 쓰는 일수 */
    public record Item(String category, long days, int defaultDays, int minDays, int maxDays) {
    }
}
