package com.community.terms.dto.response;

/**
 * 게시 당시 운영 주체(admin-web {@code TermsOperatorSnapshot}) — 서버는 사본 전체를 남기고 응답에는 세 칸만 싣는다.
 *
 * @param businessName 사업자 단계의 상호·회사명(개인은 null)
 */
public record TermsOperatorSnapshotView(String stage, String operatorName, String businessName) {
}
