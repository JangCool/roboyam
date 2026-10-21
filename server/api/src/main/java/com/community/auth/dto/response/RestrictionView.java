package com.community.auth.dto.response;

import java.time.Instant;

/**
 * 정지·영구 정지 중일 때의 제한 요약. 제재 표가 아직 없어 {@code until} 은 null, {@code pendingNotices} 는 0 이다(제재 기능을 구현할 때 채운다).
 *
 * @param status {@code SUSPENDED} · {@code BANNED}
 * @param until  진행 중인 기간 정지의 끝(ISO-8601 UTC). 그 밖에는 null
 */
public record RestrictionView(String status, Instant until, int pendingNotices) {
}
