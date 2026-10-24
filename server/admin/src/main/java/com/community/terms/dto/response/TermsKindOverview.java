package com.community.terms.dto.response;

import java.time.Instant;

/**
 * 종류 하나의 상태.
 *
 * @param kind           {@code TERMS}·{@code PRIVACY}
 * @param current        지금 약관(없으면 null)
 * @param upcoming       시행 예정 중 가장 이른 것(없으면 null)
 * @param draftUpdatedAt 초안을 마지막으로 저장한 시각(초안이 없으면 null)
 * @param versionCount   게시한 버전 수
 */
public record TermsKindOverview(String kind, TermsVersionBrief current, TermsVersionBrief upcoming, Instant draftUpdatedAt, int versionCount) {
}
