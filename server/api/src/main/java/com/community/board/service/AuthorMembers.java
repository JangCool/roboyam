package com.community.board.service;

import com.community.pointshop.dto.AuthorLook;
import com.community.user.entity.ProfileEntity;
import java.util.Map;

/**
 * 한 응답(목록·글·댓글 쪽)의 회원 작성자를 한 번에 읽어 둔 것 — 프로필(닉네임·등급)과 포인트 상점 표시(장식·아바타, {@code ShopDecorationResolver} 한 번).
 * {@link AuthorViews#members} 가 만들고 {@link AuthorViews#of} 가 읽는다.
 */
public record AuthorMembers(Map<Long, ProfileEntity> profiles, Map<Long, AuthorLook> looks) {

    public static final AuthorMembers EMPTY = new AuthorMembers(Map.of(), Map.of());

    /** 그 회원의 프로필(없으면 null) */
    public ProfileEntity get(Long userId) {
        return userId == null ? null : profiles.get(userId);
    }

    /** 그 회원의 표시(없으면 null) */
    public AuthorLook look(Long userId) {
        return userId == null ? null : looks.get(userId);
    }
}
