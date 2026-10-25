package com.community.restriction.dto.request;

import com.community.restriction.entity.RestrictionType;
import java.time.Instant;

/**
 * 운영자의 기능 제한 부여 — 검사는 서비스가 한다(칸 경로 {@code type} · {@code until} · {@code reason} · {@code adminMemo}). 시작은 서버의 지금이다.
 *
 * @param until     끝나는 순간(null = 무기한, 지금 이전이면 400)
 * @param reason    회원에게 공개하는 사유(5~200자)
 * @param adminMemo 운영 메모(~1000자, 없으면 null)
 */
public record RestrictionGrant(RestrictionType type, Instant until, String reason, String adminMemo) {
}
