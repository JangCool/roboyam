package com.community.member.dto.response;

/**
 * 회원 보기 설정(user-web {@code /settings/view}) — 야간 모드 {@code AUTO|LIGHT|DARK} · 글자 크기(백분율) · 댓글 기본 정렬 {@code OLDEST|LATEST}.
 *
 * @param own 회원이 저장한 값이 있는가 — false 면 세 칸은 기본값({@code AUTO}·{@code 100}·{@code OLDEST})
 */
public record MemberViewSettingsView(String theme, Integer textScale, String commentOrder, boolean own) {
}
