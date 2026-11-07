package com.community.terms.dto.response;

import java.time.Instant;

/**
 * 게시본 한 버전(본문 포함) — {@code GET …/versions/{version}}, 게시 결과. 본문의 자리표시는 게시 때 운영 주체 값으로 이미 바뀌어 있다.
 *
 * @param unfavorable      회원에게 불리한 변경으로 게시했는가
 * @param operatorSnapshot 게시 당시 운영 주체(운영 주체 기능 앞에 게시한 판은 null)
 */
public record TermsVersionDetailResponse(int version, String effectiveDate, Instant publishedAt, String publishedBy, String changeNote, String status,
        String body, boolean unfavorable, TermsOperatorSnapshotView operatorSnapshot) {

    @Override
    public String toString() {
        return "TermsVersionDetailResponse[version=" + version + ", effectiveDate=" + effectiveDate + ", status=" + status + "]";
    }
}
