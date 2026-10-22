package com.community.board.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.entity.BoardStaffEntity;
import com.community.board.repository.master.BoardStaffRepository;
import com.community.role.repository.master.MemberRoleQueries;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserStatus;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 「지금 쓰려는 사람」({@link BoardActor})을 요청마다 만든다 — 회원·프로필(등급)·게시판 지정·회원 역할 권한을 읽는다(요청당 몇 번의 PK·색인 조회).
 * 로그인하지 않았거나 회원이 없어졌거나 탈퇴했으면 비회원이다. 운영자는 공개 사이트에 로그인하지 않으므로 「운영진」 = 이 게시판의 매니저·스텝이다.
 */
@Component
public class BoardActorResolver {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final BoardStaffRepository boardStaff;
    private final MemberRoleQueries memberRoles;

    public BoardActorResolver(UserRepository users, ProfileRepository profiles, BoardStaffRepository boardStaff, MemberRoleQueries memberRoles) {
        this.users = users;
        this.profiles = profiles;
        this.boardStaff = boardStaff;
        this.memberRoles = memberRoles;
    }

    /** @param userId 로그인한 회원 ID(비로그인 null) */
    public BoardActor resolve(Long userId, BoardCatalogSnapshot catalog) {
        if (userId == null) {
            return BoardActor.anonymous();
        }
        Optional<UserEntity> user = users.findById(userId).filter(u -> !u.isGone());
        Optional<ProfileEntity> profile = profiles.findById(userId);
        if (user.isEmpty() || profile.isEmpty()) {
            return BoardActor.anonymous();
        }
        UserEntity u = user.get();
        String gradeKey = profile.get().getGradeKey();
        Integer level = catalog.gradeLevel(gradeKey);
        boolean restricted = u.getStatus() == UserStatus.SUSPENDED || u.getStatus() == UserStatus.BANNED;
        MemberRoleQueries.MemberRoles roles = memberRoles.of(userId, gradeKey);
        Set<Long> staffBoards = boardStaff.findByUserId(userId).stream().map(BoardStaffEntity::getBoardId).collect(Collectors.toUnmodifiableSet());
        return new BoardActor(userId, gradeKey, level == null ? 0 : level, u.isEmailVerified(), restricted, roles.business(), roles.permissions(), staffBoards);
    }
}
