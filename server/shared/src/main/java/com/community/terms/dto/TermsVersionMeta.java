package com.community.terms.dto;

import com.community.terms.entity.TermsKind;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 게시본 한 버전의 본문 뺀 값 — 목록·상태 판정용(본문은 크므로 목록 질의에서 읽지 않는다).
 *
 * @param publishedBy 게시한 운영자 ID(없을 수 있다)
 * @param changeNote  변경 요지(없으면 null)
 */
public record TermsVersionMeta(TermsKind kind, Integer version, LocalDate effectiveDate, Instant publishedAt, Long publishedBy, String changeNote) {
}
