package com.community.address.dto.response;

import com.community.address.dto.AddressProvider;
import java.time.Instant;

/**
 * 관리자 설정 › 주소 검색 — admin-web {@code AddressSettingsView} 와 같은 모양.
 *
 * @param scriptUrl        저장한 다음 스크립트 주소 — 비워 뒀으면 null(공식 주소를 쓴다)
 * @param defaultScriptUrl 공식 주소(입력칸 자리표시자)
 * @param updatedBy        마지막으로 바꾼 운영자 이름 — 바꾼 적 없거나 운영자가 지워졌으면 null
 */
public record AddressSettingsView(AddressProvider provider, String scriptUrl, String defaultScriptUrl, int staleWarnDays, int staleWarnDaysMin,
                                  int staleWarnDaysMax, AddressDbStateView db, Instant updatedAt, String updatedBy) {
}
