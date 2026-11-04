package com.community.analytics.dto;

import java.time.Instant;
import java.util.List;

/**
 * 「설정 › 통계 수집」 API 의 모양(admin-web {@code useAnalyticsSettingsApi.types.ts}, docs/features/site-analytics-contract.md 「API」).
 */
public final class AnalyticsSettingsAdmin {

    private AnalyticsSettingsAdmin() {
    }

    /** {@code PUT} 본문 — 칸이 비면 400 {@code fields.<칸>}, 보관 일수가 30·90·180·365 밖이면 400 {@code fields.rawRetentionDays} */
    public record Save(Boolean enabled, Boolean visits, Boolean rawEvents, Integer rawRetentionDays, Boolean referrers, Boolean flows, Boolean notificationSends,
                       Boolean boardActivity) {
    }

    /**
     * {@code GET}·{@code PUT} 응답 — 저장 값 그대로(방문 하위 칸은 전체가 꺼져도 값을 보인다) + 마지막 변경.
     *
     * @param updatedAt 마지막 저장 시각(ISO) — 저장한 적 없으면 null
     * @param updatedBy 마지막으로 저장한 운영자 이름 — 저장한 적 없거나 운영자가 지워졌으면 null
     */
    public record View(boolean enabled, boolean visits, boolean rawEvents, int rawRetentionDays, boolean referrers, boolean flows, boolean notificationSends,
                       boolean boardActivity, String updatedAt, String updatedBy) {
    }

    /** 이력의 {@code changes} 원소(저장 모양) — 값은 글자: 켜기 {@code true}/{@code false}, 보관 일수는 숫자 글자 */
    public record Change(String field, String before, String after) {
    }

    /** 바뀐 칸 하나 — {@code labelKey} = 화면 i18n 키 {@code settingHistory.field.*}(방문 수집 전체는 {@code analyticsEnabled}) */
    public record ChangeView(String field, String labelKey, String before, String after) {
    }

    /** 이력 한 줄 — 메일 서버·광고 자리 설정 이력과 같은 모양(admin-web {@code SettingHistoryItem}) */
    public record HistoryItem(String id, String action, String source, String operatorId, String operatorName, boolean operatorDeleted, Instant changedAt,
                              List<ChangeView> changes) {
    }

    /** 이력 한 쪽 — 최신이 위, {@code page} 는 0 부터 */
    public record HistoryPage(List<HistoryItem> items, long total, int page, int size) {
    }
}
