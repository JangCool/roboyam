package com.community.site.dto.response;

/**
 * 공개 사이트 정보의 운영 주체 — 푸터의 「문의 이메일」 한 줄(사업자 정보가 없을 때)이 쓴다. 사업자 정보 자체는 {@code GET /api/commerce/info} 가 판매 중일 때만 준다.
 *
 * @param stage        {@code INDIVIDUAL} · {@code SOLE_PROPRIETOR} · {@code CORPORATION}
 * @param contactEmail 연락 이메일(아직 저장하지 않았으면 null)
 */
public record SiteOperatorPublicView(String stage, String contactEmail) {
}
