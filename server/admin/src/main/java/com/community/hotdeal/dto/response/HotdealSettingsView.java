package com.community.hotdeal.dto.response;

import java.time.Instant;

/**
 * 핫딜 설정(GET·PUT 응답 — admin-web {@code HotdealSettingsView}) — 계약 「핫딜 설정」 표 숫자 17칸 + {@code ogFetchEnabled} + {@code affiliateMode}.
 *
 * @param rewardPoints     종료 요청 보상 포인트 — 포인트 정책 {@code dealEndRequestPoints}(읽기 전용)
 * @param rewardDailyLimit 보상 하루 지급 횟수 — 포인트 정책 {@code dealEndRequestDailyLimit}(읽기 전용)
 * @param updatedBy        마지막으로 저장한 운영자 이름(저장한 적 없으면 null)
 */
public record HotdealSettingsView(int endRequestThreshold, int endGraceHours, int endRequestDailyLimit, int endRequestPerAuthorLimit, int endRequestPerAuthorDays,
                                  int keepMaxCount, int memberDailyPostLimit, int sameStoreIntervalMinutes, int autoExpireDays, long priceMaxWon,
                                  int discountWarnPercent, int hotLikeThreshold, int suspicionMinPosts, int suspicionWindowDays, int storeSuspicionMembers,
                                  int storeSuspicionWindowDays, int storeSuspicionOtherRatioPercent, boolean ogFetchEnabled, String affiliateMode, int rewardPoints,
                                  int rewardDailyLimit, Instant updatedAt, String updatedBy) {

    /**
     * 제휴 방식 전환 영향(저장하지 않는다 — admin-web {@code HotdealAffiliateImpact}).
     *
     * @param memberAffiliateActive 진행 중 개인 제휴 글 수
     * @param siteStoreMatched      그중 상품·최종 호스트가 켠 사이트 제휴 몰인 글 수
     * @param siteStoreActive       진행 중 글 가운데 켠 사이트 제휴 몰로 가는 전체 글 수(개인 제휴 아닌 글 포함)
     */
    public record AffiliateImpact(String mode, long memberAffiliateActive, long siteStoreMatched, long siteStoreActive) {
    }
}
