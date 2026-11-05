package com.community.emoticon.dto.request;

/**
 * 수동 지급 기록({@code POST /api/admin/emoticons/settlements/{id}/manual-paid { transferDate, memo, reason }} — V149, 정산 정보 없이 탈퇴한 작가의 보류 정산).
 * 이체한 날 {@code YYYY-MM-DD}(오늘 이전 · 업무일) · 메모 200자 · 사유 5~200자(필수). 계좌는 받지 않는다(은행에서 직접 보낸 뒤 기록만). 검사는 서비스가 한다.
 */
public record EmoticonSettlementManualPaidRequest(String transferDate, String memo, String reason) {
}
