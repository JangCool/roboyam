package com.community.post.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardFields;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupStatus;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupRepository;
import com.community.post.entity.PostEntity;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 글 하나를 열 수 있는가 — 게시판·소모임 쪽 판정(19금·비밀글 판정은 따로 — {@link AdultContentGate}·{@link SecretPostReaders}). community api-groups.md 8절 「누가 읽을 수 있나」.
 * 공개 사이트(api)의 {@code GroupPostGate#resolveIgnoringAdult} 가 막힘을 오류로 바꿔 던지고, 관리자 서버에서 도는 AI 회원 쓰기({@code AiContentWriter})는 실패 사유로 바꾼다 —
 * 둘이 같은 판정을 쓰도록 shared 에 둔다(2026-11-10 — AI 실행기를 admin 으로 옮기며 api 에서 떼어 냈다).
 *
 * <ul>
 *   <li>일반 글: 공개 게시판이어야 한다 — 게시판 행이 없어졌거나 소모임 게시판이면 {@link Denial#BOARD_NOT_FOUND}, 꺼졌으면 {@link Denial#BOARD_INACTIVE}.</li>
 *   <li>소모임 글: 작성자가 나가서 가린 글 {@link Denial#POST_NOT_FOUND} → 소모임 상태(비활성 {@link Denial#GROUP_INACTIVE}, 해산은 공개 글만, 그 밖 {@link Denial#POST_NOT_FOUND})
 *       → 게시판 행 없음 {@link Denial#BOARD_NOT_FOUND} → 비멤버에게 보이지 않는 글은 멤버(모임장 포함)만 {@link Denial#GROUP_POST_PRIVATE}.</li>
 * </ul>
 */
@Component
public class PostOpenRules {

    /** 열 수 없는 까닭 */
    public enum Denial { POST_NOT_FOUND, BOARD_NOT_FOUND, BOARD_INACTIVE, GROUP_INACTIVE, GROUP_POST_PRIVATE }

    /**
     * 판정 결과 — 막혔으면 {@code denial} 만 있다. 일반 글이면 {@code group}·{@code membership} 이 null.
     *
     * @param membership 보는 사람의 지금 멤버 행(멤버가 아니거나 비회원이면 null)
     */
    public record Opened(EffectiveBoard board, GroupEntity group, GroupMemberEntity membership, Denial denial) {

        static Opened denied(Denial denial) {
            return new Opened(null, null, null, denial);
        }
    }

    private final GroupRepository groups;
    private final GroupMemberRepository members;

    public PostOpenRules(GroupRepository groups, GroupMemberRepository members) {
        this.groups = groups;
        this.members = members;
    }

    /**
     * 공개 게시판 API 가 다루는 게시판인가 — 소모임 게시판(종류 {@code GROUP})은 소모임 화면의 그릇이라 게시판 목록·상세·글 목록·글쓰기에서 <b>없는 게시판과 같다</b>
     * (docs/features/group-contract.md).
     */
    public static boolean isPublicBoard(EffectiveBoard b) {
        return !BoardFields.KIND_GROUP.equals(b.kind());
    }

    /** 공개 게시판 판정 — 없음·소모임 게시판 {@link Denial#BOARD_NOT_FOUND}, 꺼짐 {@link Denial#BOARD_INACTIVE}, 열 수 있으면 null */
    public static Denial publicBoardDenial(Optional<EffectiveBoard> board) {
        Optional<EffectiveBoard> b = board.filter(PostOpenRules::isPublicBoard);
        if (b.isEmpty()) {
            return Denial.BOARD_NOT_FOUND;
        }
        return b.get().active() ? null : Denial.BOARD_INACTIVE;
    }

    /**
     * 이 사람이 이 글을(게시판·소모임 쪽에서) 열 수 있는가. 지운 글 판정은 부르는 쪽이 먼저 한다.
     *
     * @param viewerId 보는 회원(비회원 null)
     */
    public Opened open(BoardCatalogSnapshot s, PostEntity post, Long viewerId) {
        if (!post.isGroupPost()) {
            Optional<EffectiveBoard> board = s.board(post.getBoardId());
            Denial denial = publicBoardDenial(board);
            return denial != null ? Opened.denied(denial) : new Opened(board.get(), null, null, null);
        }
        if (post.isGroupLeft()) {
            return Opened.denied(Denial.POST_NOT_FOUND); // 작성자가 소모임을 나가 가려진 글 — 글 주소는 없는 글과 같다
        }
        GroupEntity group = groups.findById(post.getGroupId()).orElse(null);
        if (group == null) {
            return Opened.denied(Denial.POST_NOT_FOUND);
        }
        if (group.getStatus() == GroupStatus.INACTIVE) {
            return Opened.denied(Denial.GROUP_INACTIVE);
        }
        boolean dissolved = group.getStatus() == GroupStatus.DELETED;
        if (group.getStatus() != GroupStatus.ACTIVE && !(dissolved && post.isPublicPost())) {
            return Opened.denied(Denial.POST_NOT_FOUND);
        }
        GroupMemberEntity membership = viewerId == null || dissolved ? null
                : members.findByGroupIdAndUserId(group.getId(), viewerId).filter(GroupMemberEntity::active).orElse(null);
        Optional<EffectiveBoard> board = s.board(post.getBoardId());
        if (board.isEmpty()) {
            return Opened.denied(Denial.BOARD_NOT_FOUND);
        }
        boolean member = viewerId != null && (group.getLeaderId().equals(viewerId) || membership != null);
        if (!post.isPublicPost() && !member) {
            return Opened.denied(Denial.GROUP_POST_PRIVATE);
        }
        return new Opened(board.get(), group, membership, null);
    }
}
