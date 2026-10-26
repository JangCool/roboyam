package com.community.pointshop.dto.response;

/** 아바타 카테고리 하나(카탈로그 차례) — 이름은 화면 문구 키({@code avatar.category.<id>}) */
public record AvatarCategoryView(String id, boolean free, int count) {
}
