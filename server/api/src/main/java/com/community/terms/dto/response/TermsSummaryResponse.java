package com.community.terms.dto.response;

import java.util.List;

/** {@code GET /api/terms/summary} — 「보기」 링크 옵션과 종류별 지금 버전·변경 안내(이용약관, 개인정보처리방침 차례) */
public record TermsSummaryResponse(boolean showLinks, List<TermsSummaryKind> kinds) {
}
