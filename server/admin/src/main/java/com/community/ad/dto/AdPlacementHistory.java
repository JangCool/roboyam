package com.community.ad.dto;

import com.community.ad.entity.AdPlacementHistoryEntity;
import java.time.Instant;
import java.util.List;

/**
 * 광고 노출 자리 설정 변경 이력({@code GET /api/admin/ads/placements/history}, V120 {@code ad_placement_history}) — 메일 서버 설정 이력({@code MailSettingHistoryPage}·
 * {@code MailSettingHistoryItemView}·{@code MailSettingChangeView})과 같은 응답 모양이라 admin-web {@code SettingHistoryDrawer} 가 그대로 그린다.
 */
public final class AdPlacementHistory {

    /** 칸 이름 — 감사 {@code AD_PLACEMENT_UPDATE} before/after 의 키와 같고, 이 차례로 남긴다 */
    public static final List<String> FIELDS = List.of("postInline.enabled", "postInline.adType", "postInline.position", "wings.left", "wings.right", "wings.screens",
            "boardList.enabled", "boardList.after", "adult.enabled");

    private AdPlacementHistory() {
    }

    /**
     * {@code ad_placement_history.changes} 배열의 원소 — 값은 모두 글자다: 켜기 {@code true}/{@code false} · 유형·위치는 이름 · {@code wings.screens} 는 HOME·PAGE·BOARD·POST
     * 차례 쉼표 이음(빈 목록은 빈 글자) · {@code boardList.after} 는 숫자 글자.
     *
     * @param before 이전 값(만들기면 null)
     */
    public record Change(String field, String before, String after) {
    }

    /** 이력 한 줄 + 바꾼 운영자의 지금 이름(지워졌으면 null) — 조회용 */
    public record Row(AdPlacementHistoryEntity history, String operatorName) {
    }

    /**
     * 바뀐 칸 하나.
     *
     * @param labelKey 칸 이름의 화면 i18n 키 {@code settingHistory.field.<field>}(예 {@code settingHistory.field.postInline.enabled}) — 문구는 admin-web 이 정한다
     */
    public record ChangeView(String field, String labelKey, String before, String after) {
    }

    /**
     * 이력 한 줄.
     *
     * @param id              Snowflake(문자열)
     * @param action          {@code CREATE}(처음 저장 — 그 전까지는 기본값) · {@code UPDATE}(고침)
     * @param source          {@code ADMIN}
     * @param operatorId      바꾼 운영자 ID(문자열). 모르면 null
     * @param operatorName    바꾼 운영자 이름 — 지워졌거나 모르면 대체 문구(「삭제된 운영자」, 요청 언어)
     * @param operatorDeleted 운영자를 찾을 수 없어 대체 문구를 넣었는가
     * @param changes         바뀐 칸 — {@link #FIELDS} 차례
     */
    public record Item(String id, String action, String source, String operatorId, String operatorName, boolean operatorDeleted, Instant changedAt,
                       List<ChangeView> changes) {
    }

    /** 이력 한 쪽 — 최신이 위, {@code page} 는 0 부터 */
    public record Page(List<Item> items, long total, int page, int size) {
    }
}
