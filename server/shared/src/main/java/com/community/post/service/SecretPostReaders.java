package com.community.post.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.service.BoardActorResolver;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupManagerPermission;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.service.GroupAccess;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.post.entity.PostEntity;
import org.springframework.stereotype.Component;

/**
 * 비밀글을 읽을 수 있는 <b>회원</b>인가(V166 · V168) — 글쓴이 · 일반 게시판이면 그 게시판의 매니저·스텝(운영자 회원 계정 포함 — {@link BoardActorResolver}) · 소모임 글이면
 * 소모임장·글 관리 매니저({@link GroupAccess#can} {@code UNPUBLISH}) · 운영자 회원 계정(정지 중 아님). 비회원 비밀글을 비밀번호로 연 방문자(세션)는 공개 사이트의
 * {@code SecretPostGate} 가 이 판정 앞에서 따로 본다. api {@code SecretPostGate} 와 관리자 서버의 AI 회원 쓰기({@code AiContentWriter})가 같이 쓰도록 shared 에 둔다
 * (2026-11-10 — AI 실행기를 admin 으로 옮기며 떼어 냈다).
 */
@Component
public class SecretPostReaders {

    private final BoardActorResolver actors;
    private final OperatorMemberAccountService operatorAccounts;

    public SecretPostReaders(BoardActorResolver actors, OperatorMemberAccountService operatorAccounts) {
        this.actors = actors;
        this.operatorAccounts = operatorAccounts;
    }

    /**
     * 이 회원이 이 비밀글을 읽는가 — 비밀글이 아니면 늘 true, 비회원(null)은 false.
     *
     * @param group      소모임 글이면 그 소모임(일반 글 null)
     * @param membership 보는 사람의 지금 멤버 행(없으면 null)
     */
    public boolean memberCanRead(BoardCatalogSnapshot s, PostEntity post, Long viewerId, GroupEntity group, GroupMemberEntity membership) {
        if (!post.isSecret()) {
            return true;
        }
        if (viewerId == null) {
            return false;
        }
        if (viewerId.equals(post.getAuthorId())) {
            return true;
        }
        if (post.isGroupPost()) {
            return (group != null && GroupAccess.can(group, membership, viewerId, GroupManagerPermission.UNPUBLISH)) || operatorAccount(s, viewerId);
        }
        return post.getBoardId() != null && actors.resolve(viewerId, s).staffOf(post.getBoardId());
    }

    /** 힘이 살아 있는 운영자 회원 계정이고 정지 중이 아닌가 */
    public boolean operatorAccount(BoardCatalogSnapshot s, long userId) {
        if (!operatorAccounts.isActiveOperatorAccount(userId)) {
            return false;
        }
        BoardActor actor = actors.resolve(userId, s);
        return !actor.guest() && !actor.restricted();
    }
}
