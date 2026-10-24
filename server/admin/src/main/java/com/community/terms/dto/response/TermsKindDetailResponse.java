package com.community.terms.dto.response;

import java.util.List;

/** {@code GET /api/admin/terms/{kind}} — 초안(없으면 null)과 이력(최신 버전 먼저) */
public record TermsKindDetailResponse(String kind, TermsDraftView draft, List<TermsVersionRow> versions) {
}
