package com.community.emoticon.dto.request;

import java.util.List;

/** 정산 확정({@code POST /api/admin/emoticons/settlements/confirm { ids }} — 정산 줄 ID 1~200개, S4). 검사는 서비스가 한다 */
public record EmoticonSettlementConfirmRequest(List<String> ids) {
}
