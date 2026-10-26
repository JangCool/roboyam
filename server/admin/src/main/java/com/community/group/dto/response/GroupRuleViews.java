package com.community.group.dto.response;

import com.community.group.dto.GroupRuleValues;
import java.time.Instant;
import java.util.List;

/**
 * 소모임 설정 네 구획의 {@code GET}·{@code PUT} 응답 — admin-web {@code useGroupSettingsApi.types.ts}. 모두 {@code {values, defaults, limits, fromDefaults, updatedAt, updatedBy}} 꼴에
 * 구획마다 칸을 더한다. {@code updatedBy} 는 마지막으로 저장한 운영자 이름(지워졌으면 「삭제된 운영자」, 저장된 적이 없으면 null).
 */
public final class GroupRuleViews {

    private GroupRuleViews() {
    }

    public record Range(int min, int max) {
    }

    /** 고르는 값 하나 — 이름은 요청 언어(등급은 등급 설정의 이름) */
    public record Option(String value, String label) {
    }

    // ── 개설 규칙 ──

    public record CreationLimits(List<Option> creationModes, List<Option> grades, Range maxPerOwner) {
    }

    public record CreationValues(String creationMode, String minGrade, int maxPerOwner) {

        public static CreationValues of(GroupRuleValues.Creation v) {
            return new CreationValues(v.creationMode().name(), v.minGrade(), v.maxPerOwner());
        }
    }

    /** @param pendingRequestCount 승인 대기 신청 수(승인제 → 자유 개설 확인 창) */
    public record Creation(CreationValues values, CreationValues defaults, CreationLimits limits, boolean fromDefaults, Instant updatedAt, String updatedBy,
                           long pendingRequestCount) {
    }

    // ── 매니저 ──

    public record ManagerLimits(Range limitDefault, Range limitMax) {
    }

    /** 열린 소모임 기준 — 임명된 매니저 수 · 매니저가 있는 소모임 수 · 소모임에 정해 둔 상한별 소모임 수(상한 오름차순) */
    public record ManagerUsage(long managerCount, long groupCount, List<LimitCount> limitCounts) {
    }

    public record LimitCount(int limit, long groupCount) {
    }

    public record Manager(GroupRuleValues.Manager values, GroupRuleValues.Manager defaults, ManagerLimits limits, boolean fromDefaults, Instant updatedAt,
                          String updatedBy, ManagerUsage usage) {
    }

    // ── 공개 범위 ──

    public record VisibilityLimits(Range cooldownDays, Range retryHours, Range rejectLimit, Range dailyLimit, Range textMax) {
    }

    /**
     * @param pendingRequestCount       열린 소모임의 처리 대기 공개 요청 수(글이 지워졌거나 가려진 요청은 뺀다)
     * @param managerNoticeSentAt       「글 공개 처리·되돌리기」 매니저 안내 알림을 보낸 시각(아직 보내지 않았으면 null — 한 번만 보낸다)
     * @param managerNoticeTargetCount  지금 「글 공개 처리·되돌리기」를 맡은 매니저 수
     */
    public record VisibilityUsage(long publicGroupCount, long privateGroupCount, long pendingRequestCount, Instant managerNoticeSentAt,
                                  long managerNoticeTargetCount) {
    }

    /** @param noticeSent 이 요청이 보낸 안내 알림 수(켜짐을 저장해 선점했을 때만 — GET·변경 없음·이미 보냄은 0) */
    public record Visibility(GroupRuleValues.Visibility values, GroupRuleValues.Visibility defaults, VisibilityLimits limits, boolean fromDefaults,
                             Instant updatedAt, String updatedBy, VisibilityUsage usage, int noticeSent) {
    }

    // ── 운영 한도 ──

    public record PolicyLimits(Range reapplyDays, Range capacityMin, Range capacityMax, Range trendingSize, Range trendingActiveDays, Range eventMaxUpcoming,
                               Range eventMaxDaysAhead, Range eventMaxLengthDays) {
    }

    public record Policy(GroupRuleValues.Policy values, GroupRuleValues.Policy defaults, PolicyLimits limits, boolean fromDefaults, Instant updatedAt,
                         String updatedBy) {
    }

    // ── 변경 이력 ──

    /**
     * 이력 한 줄 — 공용 {@code SettingHistoryItem} 모양 + {@code section}.
     *
     * @param action          CREATE(그 구획을 처음 저장 — 기본값에서) · UPDATE
     * @param source          늘 ADMIN
     * @param operatorName    저장한 운영자 이름 — 찾을 수 없으면 요청 언어의 「삭제된 운영자」
     * @param section         CREATION · MANAGER · VISIBILITY · POLICY
     */
    public record HistoryItem(String id, String action, String source, String operatorId, String operatorName, boolean operatorDeleted, Instant changedAt,
                              List<HistoryChange> changes, String section) {
    }

    /**
     * @param labelKey 칸 이름의 화면 i18n 키 {@code group.historyField.<field>}
     * @param before   전 값 — 요청 언어의 표시 글자(「승인제」·「켜짐」·「7일」·등급 이름)
     * @param after    후 값 — 같은 규칙
     */
    public record HistoryChange(String field, String labelKey, String before, String after) {
    }

    /** 이력 한 쪽 — 최신이 위, {@code page} 는 0 부터 */
    public record HistoryPage(List<HistoryItem> items, long total, int page, int size) {
    }
}
