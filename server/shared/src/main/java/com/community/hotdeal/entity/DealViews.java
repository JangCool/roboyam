package com.community.hotdeal.entity;

import java.time.Duration;
import java.time.Instant;

/** 딜 상태 계산(순수 함수) — 엔티티·목록 질의 결과·관리자 목록이 같은 판정을 쓴다 */
public final class DealViews {

    private DealViews() {
    }

    /** 기간 종료 시각 — 마감이 있으면 그 마감, 없으면 등록 + {@code autoExpireDays}(0 = 끔 → null) */
    public static Instant expiresAt(Instant endsAt, Instant createdAt, int autoExpireDays) {
        if (endsAt != null) {
            return endsAt;
        }
        return autoExpireDays > 0 && createdAt != null ? createdAt.plus(Duration.ofDays(autoExpireDays)) : null;
    }

    /** 마감이 없을 때 기간 종료로 바뀌는 시각(응답 {@code autoExpireAt}) — 마감이 있거나 설정이 꺼졌으면 null */
    public static Instant autoExpireAt(Instant endsAt, Instant createdAt, int autoExpireDays) {
        return endsAt != null ? null : expiresAt(null, createdAt, autoExpireDays);
    }

    /** ENDED > EXPIRED > FLAGGED > ACTIVE */
    public static DealView view(String status, Instant flaggedAt, Instant endsAt, Instant createdAt, int autoExpireDays, Instant now) {
        if (PostDealEntity.ENDED.equals(status)) {
            return DealView.ENDED;
        }
        Instant expires = expiresAt(endsAt, createdAt, autoExpireDays);
        if (expires != null && !now.isBefore(expires)) {
            return DealView.EXPIRED;
        }
        return flaggedAt != null ? DealView.FLAGGED : DealView.ACTIVE;
    }

    /** 할인율(정상가 > 딜가 일 때 내림 정수 %) — 아니면 null */
    public static Integer discountRate(Long normalPrice, Long dealPrice) {
        if (normalPrice == null || dealPrice == null || normalPrice <= 0 || normalPrice <= dealPrice) {
            return null;
        }
        return (int) ((normalPrice - dealPrice) * 100 / normalPrice);
    }
}
