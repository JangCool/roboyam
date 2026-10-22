package com.community.member.dto;

import com.community.grade.entity.GradeChangeKind;
import java.time.Instant;

/**
 * 등급 변경 이력 한 줄의 원본 — 조회가 읽어 온 값이다(운영자 이름은 운영자 표와 맞춰 본 것, 없으면 null).
 *
 * @param actorOperatorId 처리한 운영자 ID. 자동이면 null
 * @param actorName       그 운영자의 현재 이름. 운영자가 지워졌으면 null (그래도 {@code actorOperatorId} 는 남는다)
 */
public record MemberGradeChangeRow(Long id, String fromGradeKey, String toGradeKey, GradeChangeKind kind, String reason, String reasonCode,
                                   Long actorOperatorId, String actorName, Instant createdAt) {
}
