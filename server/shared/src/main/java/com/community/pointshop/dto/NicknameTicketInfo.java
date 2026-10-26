package com.community.pointshop.dto;

/**
 * 닉네임 변경권 — 안 쓴 장수와 한 장 값(지금 파는 변경권 품목의 가격 — 품목이 없으면 null). {@code MyProfile.nicknameChange.ticketCount|ticketPrice}·{@code GET /api/me/points} 의 {@code ticketCount}.
 *
 * @param itemId 사러 갈 변경권 품목 ID(없으면 null — 화면의 「포인트 상점에서 변경권 사기」 링크)
 */
public record NicknameTicketInfo(int count, Integer price, String itemId) {
}
