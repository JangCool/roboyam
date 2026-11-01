package com.community.hotdeal.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * 관리자 링크 규칙({@code /api/admin/hotdeal-domains})·사이트 제휴 몰({@code /api/admin/hotdeal-site-affiliates}) 응답 모양 — admin-web {@code useHotdealDomainApi.types.ts}·
 * {@code useHotdealSiteAffiliateApi.types.ts} 그대로.
 */
public final class HotdealRuleView {

    private HotdealRuleView() {
    }

    /**
     * 링크 규칙 한 줄.
     *
     * @param rule      BLOCK · AFFILIATE · null(대형몰 표시만)
     * @param updatedBy 마지막으로 고친 운영자 이름(시드는 null)
     */
    public record Domain(String id, String domain, String rule, boolean common, List<String> keyParams, String note, String updatedBy, Instant updatedAt) {
    }

    /** 규칙 목록(도메인 차례, 쪽 없음) + 지금 제휴 링크 방식 */
    public record DomainList(List<Domain> items, String affiliateMode) {
    }

    public record ImpactPost(String postId, String title, String storeName) {
    }

    /** 이 도메인(하위 도메인 포함)으로 가는 진행 중 공개 글 수 + 최근 것 3개까지 */
    public record Impact(String domain, long activeCount, List<ImpactPost> samples) {
    }

    /** 변환 방식 — TEMPLATE 이면 {@code template}, QUERY 면 {@code query} 만 나간다 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Transform(String type, String template, String query) {
    }

    public record SiteStore(String id, String domain, String programName, Transform transform, boolean enabled, String updatedBy, Instant updatedAt) {
    }

    public record SiteStoreList(List<SiteStore> items) {
    }
}
