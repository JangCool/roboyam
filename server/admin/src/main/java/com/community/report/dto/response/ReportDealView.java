package com.community.report.dto.response;

/** 신고 사건 대상이 핫딜 글일 때의 딜 상태(ACTIVE · FLAGGED · ENDED · EXPIRED — hotdeal-contract, admin-web {@code ReportCaseItem.deal}) */
public record ReportDealView(String view) {
}
