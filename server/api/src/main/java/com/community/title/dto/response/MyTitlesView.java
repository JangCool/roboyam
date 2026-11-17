package com.community.title.dto.response;

import com.community.title.dto.response.OwnedTitleView;
import java.time.Instant;
import java.util.List;

/**
 * 내 칭호({@code GET /api/me/titles} — 계약 「구현 결정」 7).
 *
 * @param representative 고른 대표 칭호(지금도 가진 것) — 고르지 않았으면(기본 = 가진 작가 칭호 중 가장 높은 것)·「안 보이기」면 null
 * @param hidden         「안 보이기」를 골랐나
 * @param owned          가진 칭호(공개 프로필 줄과 같은 모양 — 꺼진 칭호 빼고)
 * @param progress       자동 칭호의 다음 목표(다 받은 칭호는 빠진다) — {@code tier} 는 다음 단계(단계 없는 칭호는 null)
 * @param applicable     신청형 칭호 중 아직 없는 것 — 조건별 채움·신청 가능·다시 신청할 수 있는 날
 * @param applications   내 신청(최신 순 50개)
 * @param unseen         아직 축하 창을 보지 않은 받은 칭호 — 닫으면 {@code POST /api/me/titles/seen}
 */
public record MyTitlesView(RepresentativeView representative, boolean hidden, List<OwnedTitleView> owned, List<ProgressView> progress, List<ApplicableView> applicable,
                           List<ApplicationView> applications, List<UnseenView> unseen) {

    public record RepresentativeView(String key) {
    }

    /** 진행 막대 하나 */
    public record ProgressView(String key, String label, String labelEn, String icon, String chip, String tier, long current, long target) {
    }

    /** 신청형 칭호 하나 */
    public record ApplicableView(String key, String label, String labelEn, String icon, String chip, List<ConditionView> conditions, boolean canApply, Instant reapplyAt,
                                 boolean pending) {
    }

    /** 조건 한 줄 — {@code label} 요청 언어, {@code current}·{@code target} 은 세는 조건만(그 밖 null) */
    public record ConditionView(String type, String label, boolean met, Long current, Long target) {
    }

    /** 내 신청 하나 — {@code status} PENDING·APPROVED·REJECTED, 거절이면 {@code reason}·{@code reapplyAt}. {@code labelEn} 칭호 영어 이름(없으면 null — 점검 D14) */
    public record ApplicationView(String id, String key, String label, String labelEn, String status, String reason, Instant reapplyAt, Instant createdAt) {
    }

    /** 축하 창 하나 — {@code ratio} 그 칭호 보유 비율 0~1 */
    public record UnseenView(String key, String label, String labelEn, String icon, String tier, String chip, double ratio) {
    }
}
