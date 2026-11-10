package com.community.site.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET·PUT /api/admin/site/operator} — 운영 주체 값 + 마지막 저장 시각 + 단계 전환 이력(최신 먼저). admin-web {@code SiteOperatorResponse} 와 같은 모양.
 * 빈 선택 칸은 null, 개인 단계는 {@code business} 가 null.
 *
 * @param footerNotice      푸터 안내 문구 — 줄을 {@code \n} 으로 이은 글(앞뒤 공백 뗀 빈 줄 없는 줄들), 숨김이면 null. {@code {사이트}} 는 그대로
 * @param footerNoticeScope {@code INDIVIDUAL_ONLY} · {@code ALWAYS}
 * @param updatedAt         마지막 저장 시각(저장한 적 없으면 null)
 */
public record SiteOperatorResponse(String stage, String operatorName, String contactEmail, String phone, boolean privacyOfficerSameAsOperator,
                                   String privacyOfficerName, String privacyOfficerEmail, String hostingProvider, String mailProvider, Business business,
                                   String footerNotice, String footerNoticeScope, Instant updatedAt, List<StageChange> history) {

    public record Business(String businessName, String representative, String registrationNumber, String address, String mailOrderNumber,
                           String mailOrderAuthority, String corporateNumber) {
    }

    /** @param changedBy 처리한 운영자 표시 이름(없으면 null — 지워진 운영자·마이그레이션) */
    public record StageChange(Instant changedAt, String fromStage, String toStage, String changedBy) {
    }

    @Override
    public String toString() {
        return "SiteOperatorResponse[stage=" + stage + ", updatedAt=" + updatedAt + "]";
    }
}
