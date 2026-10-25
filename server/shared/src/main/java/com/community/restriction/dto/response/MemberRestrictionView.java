package com.community.restriction.dto.response;

import com.community.restriction.entity.RestrictionSource;
import com.community.restriction.entity.RestrictionStatus;
import com.community.restriction.entity.RestrictionType;
import java.time.Instant;

/**
 * 기능 제한 한 건 — 관리자 회원 상세 「기능 제한」(계약 「0-4」 {@code GET /api/admin/members/{memberId}/restrictions} 의 한 줄, 부여·해제 응답).
 * 운영자 화면 전용이라 운영 메모가 있다(회원에게 가는 안내·알림에는 싣지 않는다).
 *
 * @param status    지금 시각으로 판정한 상태
 * @param autoRule  자동 부여 규칙 코드({@code AUTO} 일 때만)
 * @param reason    회원에게 공개하는 사유(자동 부여는 규칙의 번들 문구 — 요청 언어)
 * @param until     끝나는 순간(무기한이면 null)
 * @param createdBy 부여한 운영자(자동이면 null)
 */
public record MemberRestrictionView(String id, RestrictionType type, RestrictionStatus status, RestrictionSource source, String autoRule, String reason,
                                    String adminMemo, Instant startsAt, Instant until, RestrictionActorView createdBy, Instant createdAt,
                                    RestrictionActorView liftedBy, Instant liftedAt, String liftReason) {
}
