package com.community.pointshop.dto.response;

/** 구매한 회원 — 탈퇴 회원이면 {@code id} 가 null */
public record PointShopPurchaseMemberView(String id, String nickname, boolean withdrawn) {
}
