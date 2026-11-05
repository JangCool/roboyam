package com.community.board.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.entity.BoardStaffEntity;
import com.community.board.repository.master.BoardStaffRepository;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.role.repository.master.MemberRoleQueries;
import com.community.sanction.service.BoardSanctionService;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserStatus;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 「지금 쓰려는 사람」({@link BoardActor})을 요청마다 만든다 — 회원·프로필(등급)·게시판 지정·회원 역할 권한을 읽는다(요청당 몇 번의 PK·색인 조회).
 * 로그인하지 않았거나 회원이 없어졌거나 탈퇴했으면 비회원이다. 「운영진」 = 이 게시판의 매니저·스텝, 그리고 <b>힘이 살아 있는 운영자 회원 계정</b>(모든 공개 게시판 —
 * 소모임 게시판 제외, operator-member-account-contract 정책 8: 운영진 전용 말머리 · 운영진 전용 글쓰기 · 쓰기 잠금 면제, 권한과 무관).
 */
@Component
public class BoardActorResolver {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final BoardStaffRepository boardStaff;
    private final MemberRoleQueries memberRoles;
    private final BoardSanctionService boardSanctions;
    private final OperatorMemberAccountService operatorAccounts;

    public BoardActorResolver(UserRepository users, ProfileRepository profiles, BoardStaffRepository boardStaff, MemberRoleQueries memberRoles,
                              BoardSanctionService boardSanctions, OperatorMemberAccountService operatorAccounts) {
        this.operatorAccounts = operatorAccounts;
        this.boardSanctions = boardSanctions;
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
        // 탈퇴 진행 중(WITHDRAWING)도 새 활동(글·댓글·쪽지 보내기·소모임·핫딜·신고·이미지)을 막는다 — 정지와 같은 길(member-withdrawal-contract 「권한·정책」)
        boolean restricted = u.getStatus() == UserStatus.SUSPENDED || u.getStatus() == UserStatus.BANNED || u.getStatus() == UserStatus.WITHDRAWING;
        MemberRoleQueries.MemberRoles roles = memberRoles.of(userId, gradeKey);
        Set<Long> staffBoards = boardStaff.findByUserId(userId).stream().map(BoardStaffEntity::getBoardId).collect(Collectors.toUnmodifiableSet());
        if (!restricted && operatorAccounts.isActiveOperatorAccount(userId)) {
            // 운영자 회원 계정 — 모든 공개 게시판(소모임 게시판 제외)의 운영진(정책 8). 힘은 요청마다 판정한다(캐시 없음)
            Set<Long> all = new HashSet<>(staffBoards);
            catalog.boards().stream().filter(b -> !BoardFields.KIND_GROUP.equals(b.kind())).forEach(b -> all.add(b.id()));
            staffBoards = Set.copyOf(all);
        }
        // 게시판 제재의 진행 중 쓰기 제한(board-sanction-contract 결정 5) — 회원 범위 부분 색인 질의 하나(ux_board_sanctions_running)
        return new BoardActor(userId, gradeKey, level == null ? 0 : level, u.isEmailVerified(), restricted, roles.business(), roles.permissions(), staffBoards,
                boardSanctions.writeRestrictions(userId));
    }
}
