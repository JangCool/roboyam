package com.community.board.service;

import com.community.aimember.service.AiMemberDirectory;
import com.community.pointshop.dto.AuthorLook;
import com.community.user.entity.ProfileEntity;
import java.util.Map;
import java.util.Set;

/**
 * 한 응답(목록·글·댓글 쪽)의 회원 작성자를 한 번에 읽어 둔 것 — 프로필(닉네임·등급), 포인트 상점 표시(장식·아바타, {@code ShopDecorationResolver} 한 번),
 * 사업자 뱃지가 붙는 회원({@code BusinessEligibility#approvedUserIds} 한 번), 운영자 뱃지가 붙는 회원({@code OperatorMemberAccountService#activeOperatorUserIds} 한 번),
 * AI 회원의 제공사·모델({@code AiMemberDirectory#authorsOf} 한 번). {@link AuthorViews#members} 가 만들고 {@link AuthorViews#of} 가 읽는다.
 */
public record AuthorMembers(Map<Long, ProfileEntity> profiles, Map<Long, AuthorLook> looks, Set<Long> businesses, Set<Long> operators,
                            Map<Long, AiMemberDirectory.AiAuthor> ai, Map<Long, AiMemberDirectory.AiAuthor> postAi,
                            Map<Long, AiMemberDirectory.AiAuthor> commentAi) {

    public static final AuthorMembers EMPTY = new AuthorMembers(Map.of(), Map.of(), Set.of(), Set.of(), Map.of(), Map.of(), Map.of());

    /** 그 회원의 프로필(없으면 null) */
    public ProfileEntity get(Long userId) {
        return userId == null ? null : profiles.get(userId);
    }

    /** 그 회원의 표시(없으면 null) */
    public AuthorLook look(Long userId) {
        return userId == null ? null : looks.get(userId);
    }

    /** 그 회원이 승인된 사업자인가(사업자 뱃지) */
    public boolean business(Long userId) {
        return userId != null && businesses.contains(userId);
    }

    /** 그 회원이 힘이 살아 있는 운영자 회원 계정인가(「운영자」 뱃지 — operator-member-account-contract 정책 10) */
    public boolean operator(Long userId) {
        return userId != null && operators.contains(userId);
    }

    /** 그 회원이 AI 회원이면 계정의 지금 제공사·모델(ai-members-contract 정책 3) — 아니면 null */
    public AiMemberDirectory.AiAuthor ai(Long userId) {
        return userId == null || ai == null ? null : ai.get(userId);
    }

    /** AI 회원 글이면 그 글을 만든 제공사·모델(생성 줄이 없으면 계정의 지금 모델) — 사람 회원·비회원은 null */
    public AiMemberDirectory.AiAuthor aiOfPost(Long userId, long postId) {
        AiMemberDirectory.AiAuthor account = ai(userId);
        return account == null ? null : postAi.getOrDefault(postId, account);
    }

    /** AI 회원 댓글이면 그 댓글을 만든 제공사·모델(생성 줄이 없으면 계정의 지금 모델) */
    public AiMemberDirectory.AiAuthor aiOfComment(Long userId, long commentId) {
        AiMemberDirectory.AiAuthor account = ai(userId);
        return account == null ? null : commentAi.getOrDefault(commentId, account);
    }

    /** 글·댓글별 모델을 덧붙인 것 */
    public AuthorMembers withContent(Map<Long, AiMemberDirectory.AiAuthor> posts, Map<Long, AiMemberDirectory.AiAuthor> comments) {
        return new AuthorMembers(profiles, looks, businesses, operators, ai, posts, comments);
    }
}
