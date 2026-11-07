package com.community.terms.dto.response;

import java.time.Instant;

/**
 * 이력 표의 한 줄.
 *
 * @param publishedBy      게시한 운영자 표시 이름(없으면 null)
 * @param status           {@code CURRENT}·{@code UPCOMING}·{@code PAST}
 * @param unfavorable      회원에게 불리한 변경으로 게시했는가
 * @param operatorSnapshot 게시 당시 운영 주체(운영 주체 기능 앞에 게시한 판은 null)
 */
public record TermsVersionRow(int version, String effectiveDate, Instant publishedAt, String publishedBy, String changeNote, String status, boolean unfavorable,
                              TermsOperatorSnapshotView operatorSnapshot) {
}
