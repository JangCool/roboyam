package com.community.restriction.service;

import com.community.member.service.MemberLookup;
import com.community.permission.dto.AdminActor;
import com.community.restriction.dto.request.MemberRestrictionCreateRequest;
import com.community.restriction.dto.response.MemberRestrictionView;
import com.community.restriction.exception.RestrictionNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 관리자 회원 상세 「기능 제한」 구획(계약 「0-4」) — 경로의 번호를 바꾸고 shared {@link MemberRestrictionService} 를 부른다. 업무 규칙(진행 중 판정·탈퇴 회원 409·조건부 해제·감사·회원 알림)은
 * 모두 그쪽에 있다. 권한(조회 {@code member.read}, 걸기·해제 {@code sanction.manage})은 컨트롤러 선언이 정한다.
 */
@Service
public class MemberRestrictionAdminService {

    private final MemberLookup members;
    private final MemberRestrictionService restrictions;

    public MemberRestrictionAdminService(MemberLookup members, MemberRestrictionService restrictions) {
        this.members = members;
        this.restrictions = restrictions;
    }

    /** 진행 중 먼저, 그다음 최근 것부터. 없는 회원은 404 {@code MEMBER_NOT_FOUND} */
    public List<MemberRestrictionView> list(String memberId) {
        return restrictions.list(members.require(memberId).id());
    }

    /** 걸기 — 400 {@code VALIDATION} · 404 {@code MEMBER_NOT_FOUND} · 409 {@code MEMBER_WITHDRAWN} */
    public MemberRestrictionView grant(AdminActor actor, String memberId, MemberRestrictionCreateRequest request) {
        return restrictions.grant(MemberLookup.parseId(memberId), request.toGrant(), actor.operatorId(), actor.ip());
    }

    /** 해제 — 400 {@code VALIDATION}(사유) · 404 {@code RESTRICTION_NOT_FOUND}(없음·남의 것) · 409 {@code RESTRICTION_NOT_ACTIVE} */
    public MemberRestrictionView lift(AdminActor actor, String memberId, String restrictionId, String reason) {
        long member = MemberLookup.parseId(memberId);
        return restrictions.lift(member, parseRestrictionId(restrictionId), reason, actor.operatorId(), actor.ip());
    }

    private static long parseRestrictionId(String restrictionId) {
        if (restrictionId == null || restrictionId.isEmpty() || restrictionId.length() > 19 || !restrictionId.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            throw new RestrictionNotFoundException();
        }
        try {
            return Long.parseLong(restrictionId);
        } catch (NumberFormatException e) {
            throw new RestrictionNotFoundException();
        }
    }
}
