package com.community.terms.dto;

import com.community.terms.dto.response.PublicTermsPageResponse;
import com.community.terms.dto.response.TermsSummaryResponse;
import com.community.terms.entity.TermsKind;
import java.util.Map;

/** 공개 약관의 짧은 캐시 사본 — 요약과 종류별 화면 응답 */
public record PublicTermsSnapshot(TermsSummaryResponse summary, Map<TermsKind, PublicTermsPageResponse> pages) {
}
