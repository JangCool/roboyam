package com.community.pointshop.dto;

import com.community.pointshop.dto.response.AuthorDecorationView;

/**
 * 작성자 한 명의 표시 — 장식(만료된 장착은 빠진다, 둘 다 없으면 null)과 아바타 주소(고르지 않았거나 카탈로그에 없으면 {@code basic-default}). {@code ShopDecorationResolver} 가 만든다.
 * {@code avatarStillUrl} 은 움직이는 아바타(카탈로그 움직이는 것·움직이는 GIF 를 올린 것)의 정지 그림 — 정지 아바타는 null(commerce-motion-avatar-contract 「표시」).
 */
public record AuthorLook(AuthorDecorationView decoration, String avatarUrl, String avatarStillUrl) {
}
