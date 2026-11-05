package com.community.emoticon.dto.request;

/** 지급 완료 기록({@code POST /api/admin/emoticons/settlements/{id}/paid { transferDate, memo }} — 이체한 날 {@code YYYY-MM-DD} · 메모 200자, S4). 검사는 서비스가 한다 */
public record EmoticonSettlementPaidRequest(String transferDate, String memo) {
}
