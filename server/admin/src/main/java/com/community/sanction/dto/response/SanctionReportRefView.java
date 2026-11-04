package com.community.sanction.dto.response;

/** 근거 신고 사건 — {@code caseNo} 는 신고 관리의 사건 번호 */
public record SanctionReportRefView(String id, long caseNo) {
}
