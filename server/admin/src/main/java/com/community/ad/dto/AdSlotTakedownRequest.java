package com.community.ad.dto;

import java.util.List;

/**
 * 광고 자리 관리의 여러 건 긴급 내림 — 검사는 서비스(400 {@code fields.ids}·{@code fields.reason}). 사유 하나로 건마다 단건 긴급 내림과 같은 규칙.
 *
 * @param ids              광고 건(신청 건) ID 1~50개 — 같은 ID 는 한 번만
 * @param reason           사유(단건 긴급 내림과 같은 검사)
 * @param notifyAdvertiser 광고주 메일(건마다 커밋 뒤)
 */
public record AdSlotTakedownRequest(List<String> ids, String reason, Boolean notifyAdvertiser) {
}
