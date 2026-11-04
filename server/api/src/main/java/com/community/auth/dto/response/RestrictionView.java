package com.community.auth.dto.response;

import java.time.Instant;

/**
 * 로그인 정보의 제한 요약(member-sanction-contract) — 정지·영구 정지 중이거나 확인하지 않은 경고가 있을 때만 있다.
 *
 * @param status         {@code SUSPENDED} · {@code BANNED} · {@code ACTIVE}(확인할 경고만 있을 때)
 * @param until          진행 중인 기간 정지의 끝(ISO-8601 UTC). 그 밖에는 null
 * @param pendingNotices 아직 확인하지 않은 유효한 경고 수
 */
public record RestrictionView(String status, Instant until, int pendingNotices) {
}
