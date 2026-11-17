package com.community.title.dto.request;

/**
 * 칭호 수여({@code POST /api/admin/titles/{key}/grants}) — {@code memberId} 회원 ID(글자), {@code period} 이달의 작가 {@code YYYY-MM}·올해의 작가 {@code YYYY}(그 밖 비움),
 * {@code tier} 단계 칭호의 단계(없으면 동), {@code reason} 사유(선택, 500자).
 */
public record AdminTitleGrantRequest(String memberId, String period, String tier, String reason) {
}
