package com.community.hotdeal.dto;

/**
 * 핫딜 설정(V122 {@code deal_settings} 한 행) — 계약 「핫딜 설정」 표. 범위는 DB CHECK 가 막고, 읽을 때 행이 없으면 {@link #DEFAULTS}.
 * 보상 포인트·하루 지급 횟수는 포인트 정책 소유라 여기 없다. OG 보안 값(시간 제한·크기·동시 수)은 설정이 아니라 코드 상수다(서버 규칙 1).
 *
 * @param affiliateMode {@link #MEMBER}(개인 제휴 허용, 기본) · {@link #SITE}(사이트 제휴 — D5)
 */
public record DealSettings(int endRequestThreshold, int endGraceHours, int endRequestDailyLimit, int endRequestPerAuthorLimit, int endRequestPerAuthorDays,
                           int keepMaxCount, int memberDailyPostLimit, int sameStoreIntervalMinutes, int autoExpireDays, long priceMaxWon, int discountWarnPercent,
                           int hotLikeThreshold, int suspicionMinPosts, int suspicionWindowDays, int storeSuspicionMembers, int storeSuspicionWindowDays,
                           int storeSuspicionOtherRatioPercent, boolean ogFetchEnabled, String affiliateMode) {

    public static final String MEMBER = "MEMBER";
    public static final String SITE = "SITE";

    public static final DealSettings DEFAULTS = new DealSettings(3, 12, 20, 3, 7, 2, 5, 10, 30, 100_000_000L, 90, 10, 3, 7, 3, 30, 20, true, MEMBER);

    public boolean siteMode() {
        return SITE.equals(affiliateMode);
    }
}
