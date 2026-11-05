package com.community.board.service;

import com.community.pointshop.dto.AuthorLook;
import com.community.user.entity.ProfileEntity;
import java.util.Map;
import java.util.Set;

/**
 * 한 응답(목록·글·댓글 쪽)의 회원 작성자를 한 번에 읽어 둔 것 — 프로필(닉네임·등급), 포인트 상점 표시(장식·아바타, {@code ShopDecorationResolver} 한 번),
 * 사업자 뱃지가 붙는 회원({@code BusinessEligibility#approvedUserIds} 한 번), 운영자 뱃지가 붙는 회원({@code OperatorMemberAccountService#activeOperatorUserIds} 한 번). {@link AuthorViews#members} 가 만들고 {@link AuthorViews#of} 가 읽는다.
 */
public record AuthorMembers(Map<Long, ProfileEntity> profiles, Map<Long, AuthorLook> looks, Set<Long> businesses, Set<Long> operators) {

    public static final AuthorMembers EMPTY = new AuthorMembers(Map.of(), Map.of(), Set.of(), Set.of());

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
}
