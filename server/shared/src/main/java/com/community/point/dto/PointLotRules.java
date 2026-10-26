package com.community.point.dto;

import java.time.Instant;

/**
 * 묶음 정책(V85 {@code point_settings} 의 새 칸) — 관리자 정책 화면의 14칸({@link PointRules})과 따로 둔다(화면 칸은 1-3 에서).
 *
 * @param freeValidDays         무상 묶음 유효기간(일)
 * @param restoredLotValidDays  만료된 묶음을 되돌릴 때 새 묶음의 유효기간(일)
 * @param expiryNoticeDays      소멸 예정 알림을 며칠 앞에 보내나(알림 배치)
 * @param lotTrackingStartedAt  묶음 추적을 시작한 시각(전환 시각) — 불변식은 이 뒤 줄만 묶음 반영을 본다
 * @param expiryNoticeIntervalDays 한 회원에게 소멸 예정 알림을 다시 보내기까지 최소 며칠(0 = 간격 없음)
 * @param adjustLargeThreshold  |운영 조정| 이 이 값을 넘으면 {@code point.adjust.large} 가 필요하다(0 = 모든 조정이 고액)
 */
public record PointLotRules(int freeValidDays, int restoredLotValidDays, int expiryNoticeDays, int expiryNoticeIntervalDays, Instant lotTrackingStartedAt,
                            int adjustLargeThreshold) {

    /** 정책 행이 없을 때(V85 기본값과 같다 — 전환 시각은 모른다) */
    public static final PointLotRules DEFAULTS = new PointLotRules(365, 30, 30, 7, null, 10_000);
}
