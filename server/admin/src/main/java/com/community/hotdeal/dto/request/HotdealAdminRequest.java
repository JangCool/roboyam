package com.community.hotdeal.dto.request;

import java.util.List;

/**
 * 관리자 핫딜 API 의 요청 모양 — admin-web 타입 파일 그대로. 검사 순서·칸 경로가 계약이라 Bean Validation 없이 서비스가 {@code fields.<칸>} 으로 모은다.
 */
public final class HotdealAdminRequest {

    private HotdealAdminRequest() {
    }

    /** 운영진 종료·재개 — 사유 0~200자(감사·작성자 알림) */
    public record Action(String reason) {
    }

    /** 링크 규칙 추가·고치기 — {@code rule} BLOCK · AFFILIATE · null(대형몰 표시만 — 그러면 {@code common} 필수) */
    public record Domain(String domain, String rule, Boolean common, List<String> keyParams, String note) {
    }

    /** 의심 후보 무시 — 메모 0~200자 */
    public record Dismiss(String note) {
    }

    /** 핫딜 설정 저장(통째로) */
    public record Settings(Integer endRequestThreshold, Integer endGraceHours, Integer endRequestDailyLimit, Integer endRequestPerAuthorLimit,
                           Integer endRequestPerAuthorDays, Integer keepMaxCount, Integer memberDailyPostLimit, Integer sameStoreIntervalMinutes,
                           Integer autoExpireDays, Long priceMaxWon, Integer discountWarnPercent, Integer hotLikeThreshold, Integer suspicionMinPosts,
                           Integer suspicionWindowDays, Integer storeSuspicionMembers, Integer storeSuspicionWindowDays, Integer storeSuspicionOtherRatioPercent,
                           Boolean ogFetchEnabled, String affiliateMode) {
    }

    /** 사이트 제휴 몰의 변환 방식 — {@code {type: TEMPLATE, template}} · {@code {type: QUERY, query}} */
    public record Transform(String type, String template, String query) {
    }

    /** 사이트 제휴 몰 추가·고치기(켬/끔도 줄 전체) */
    public record SiteStore(String domain, String programName, Transform transform, Boolean enabled) {
    }
}
