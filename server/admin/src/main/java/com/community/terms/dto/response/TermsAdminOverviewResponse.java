package com.community.terms.dto.response;

import java.util.List;

/** {@code GET /api/admin/terms} — 「보기」 링크 옵션과 종류별 상태(이용약관, 개인정보처리방침 차례) */
public record TermsAdminOverviewResponse(boolean showLinks, List<TermsKindOverview> kinds) {
}
