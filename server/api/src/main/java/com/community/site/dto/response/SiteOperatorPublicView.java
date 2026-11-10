package com.community.site.dto.response;

import java.util.List;

/**
 * 공개 사이트 정보의 운영 주체 — 푸터의 「문의 이메일」 한 줄(사업자 정보가 없을 때)과 푸터 안내 문구가 쓴다. 사업자 정보 자체는 {@code GET /api/commerce/info} 가 판매 중일 때만 준다.
 *
 * @param stage             {@code INDIVIDUAL} · {@code SOLE_PROPRIETOR} · {@code CORPORATION}
 * @param contactEmail      연락 이메일(아직 저장하지 않았으면 null)
 * @param footerNotice      푸터 안내 줄(최대 3줄, 앞뒤 공백 뗀 빈 줄 없는 줄) — 숨김이면 null. {@code {사이트}} 와 뒤 조사는 그대로(화면이 사이트 이름으로 바꾼다)
 * @param footerNoticeScope {@code INDIVIDUAL_ONLY}(개인 단계에서만 보이기) · {@code ALWAYS}(늘 보이기)
 */
public record SiteOperatorPublicView(String stage, String contactEmail, List<String> footerNotice, String footerNoticeScope) {
}
