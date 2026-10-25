package com.community.withdrawal.service;

import com.community.member.service.MemberLookup;
import com.community.permission.dto.AdminActor;
import com.community.withdrawal.dto.RevealedContact;
import com.community.withdrawal.dto.WithdrawnContactInfo;
import com.community.withdrawal.dto.response.WithdrawnContactRevealView;
import com.community.withdrawal.dto.response.WithdrawnContactView;
import com.community.withdrawal.exception.WithdrawnContactNotFoundException;
import com.community.zconfiguration.time.ServiceTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 관리자 회원 상세의 탈퇴 회원 연락처(분리 보관 — commerce.md 17-2, 계약 「0-4」). 표를 읽고 값을 푸는 일은 shared {@link WithdrawnContactStore} 만 한다 — 여기는 회원 번호·탈퇴 여부를 확인하고
 * 화면 모양으로 바꾼다. 권한(상태 {@code member.read}, 열람 {@code member.withdrawn.contact})은 컨트롤러 선언이 정한다.
 */
@Service
public class WithdrawnContactAdminService {

    private final MemberLookup members;
    private final WithdrawnContactStore contacts;

    public WithdrawnContactAdminService(MemberLookup members, WithdrawnContactStore contacts) {
        this.members = members;
        this.contacts = contacts;
    }

    /** 보관 여부·파기 예정일·열람 기록(이메일 없음). 없는 회원은 404 {@code MEMBER_NOT_FOUND} */
    public WithdrawnContactView view(String memberId) {
        MemberLookup.Member member = members.require(memberId);
        Optional<WithdrawnContactInfo> info = member.withdrawn() ? contacts.find(member.id()) : Optional.empty();
        List<WithdrawnContactRevealView> reveals = contacts.reveals(member.id()).stream()
                .map(reveal -> new WithdrawnContactRevealView(reveal.operatorName(), reveal.revealedAt(), reveal.reason())).toList();
        return new WithdrawnContactView(info.isPresent(), info.map(WithdrawnContactInfo::withdrawnAt).orElse(member.withdrawnAt()),
                info.map(i -> ServiceTime.dateOf(i.purgeAfter())).orElse(null), reveals);
    }

    /**
     * 사유를 남기고 연다 — 감사를 같은 트랜잭션에 남긴 뒤에만 값을 준다({@link WithdrawnContactStore#reveal}).
     *
     * @throws WithdrawnContactNotFoundException 탈퇴하지 않은 회원이거나 보관한 연락처가 없을 때(404)
     */
    public RevealedContact reveal(AdminActor actor, String memberId, String reason) {
        MemberLookup.Member member = members.require(memberId);
        if (!member.withdrawn()) {
            throw new WithdrawnContactNotFoundException();
        }
        return contacts.reveal(member.id(), reason, actor.operatorId(), actor.ip());
    }
}
