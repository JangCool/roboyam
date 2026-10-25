package com.community.terms.dto.response;

import java.util.List;

/**
 * {@code GET /api/terms/{kind}} — 지금 약관(본문 포함, 없으면 null = 빈 상태), 시행 예정(가장 이른 것, 없으면 null), 모든 게시본 목록(최신 버전 먼저).
 */
public record PublicTermsPageResponse(String kind, PublicTermsCurrent current, PublicTermsUpcoming upcoming, List<PublicTermsHistoryItem> history) {
}
