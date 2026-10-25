package com.community.restriction.dto.request;

import com.community.restriction.entity.RestrictionType;
import java.time.Instant;

/**
 * 기능 제한 걸기({@code POST /api/admin/members/{memberId}/restrictions}) — 검사는 서비스({@code MemberRestrictionService#grant})가 칸별로 한다
 * ({@code fields.type|until|reason|adminMemo}: 종류 필수 · 끝은 지금 뒤 · 사유 5~200자 · 메모 ~1000자). 시작은 서버의 지금이다.
 *
 * @param until     끝나는 순간(ISO UTC, null = 무기한)
 * @param reason    회원에게 공개하는 사유
 * @param adminMemo 운영 메모(회원에게 보내지 않는다, 없으면 null)
 */
public record MemberRestrictionCreateRequest(RestrictionType type, Instant until, String reason, String adminMemo) {

    public RestrictionGrant toGrant() {
        return new RestrictionGrant(type, until, reason, adminMemo);
    }
}
