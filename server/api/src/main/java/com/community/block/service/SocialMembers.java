package com.community.block.service;

import com.community.operator.service.OperatorMemberAccountService;
import com.community.block.exception.MemberProfileNotFoundException;
import com.community.board.repository.master.BoardStaffRepository;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 차단·메모·쪽지의 상대 회원 찾기 — 없는·탈퇴한·형식이 틀린 회원은 모두 404 {@code PROFILE_NOT_FOUND}(있는지 가려 알리지 않는다).
 * 「운영진」은 기존 판정 그대로 게시판 매니저·스텝을 하나라도 맡은 회원이다(공개 사이트에는 운영자가 로그인하지 않는다 — {@code PublicMenuService}·{@code BoardActorResolver}).
 */
@Component
public class SocialMembers {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final BoardStaffRepository boardStaff;
    private final OperatorMemberAccountService operatorAccounts;

    public SocialMembers(UserRepository users, ProfileRepository profiles, BoardStaffRepository boardStaff, OperatorMemberAccountService operatorAccounts) {
        this.operatorAccounts = operatorAccounts;
        this.users = users;
        this.profiles = profiles;
        this.boardStaff = boardStaff;
    }

    /** 살아 있는 회원 한 명(계정·프로필) */
    public record Member(UserEntity user, ProfileEntity profile) {

        public long id() {
            return user.getId();
        }
    }

    /** 회원 ID 글자 → 살아 있는 회원. 없으면 404 */
    public Member require(String idText) {
        Long id = SocialPaging.id(idText);
        if (id == null) {
            throw new MemberProfileNotFoundException();
        }
        return find(id).orElseThrow(MemberProfileNotFoundException::new);
    }

    public Optional<Member> find(long id) {
        // 탈퇴 진행 중 회원은 공개 노출(차단·메모 대상 찾기)에서 빠진다(member-withdrawal-contract)
        Optional<UserEntity> user = users.findById(id).filter(u -> !u.isGone() && u.getStatus() != com.community.user.entity.UserStatus.WITHDRAWING);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        return profiles.findById(id).map(p -> new Member(user.get(), p));
    }

    /** 공개 사이트의 운영진인가(게시판 매니저·스텝 · 힘이 살아 있는 운영자 회원 계정 — operator-member-account-contract 정책 9) */
    public boolean staff(long userId) {
        return boardStaff.countByUserId(userId) > 0 || operatorAccounts.isActiveOperatorAccount(userId);
    }
}
