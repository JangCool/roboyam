package com.community.point.dto;

import java.time.Instant;
import java.util.List;

/**
 * 검사를 마친 원장 조회 조건 — 비어 있는 조건은 null.
 *
 * @param kind    {@code EARN} · {@code USE} · {@code REVOKE}
 * @param reasons 사유 코드들(비어 있으면 거르지 않는다)
 * @param q       회원 검색어(소문자로 다듬은 값)
 */
public record PointLedgerFilter(Instant from, Instant to, String kind, List<String> reasons, String q) {
}
