package com.community.pointshop.dto.response;

/**
 * 아바타 카테고리 하나(카탈로그 차례) — 이름은 화면 문구 키({@code avatar.category.<id>}). {@code animated} 는 움직이는 아바타 카테고리({@code motion}).
 * 승인된 내가 올린 아바타가 있으면 카테고리 {@code upload}(한 줄)가 무료 카테고리 바로 뒤에 생긴다.
 */
public record AvatarCategoryView(String id, boolean free, boolean animated, int count) {
}
