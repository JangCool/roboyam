package com.community.member.dto.response;

/**
 * 회원 보기 설정(user-web {@code /settings/view}) — 야간 모드 {@code AUTO|LIGHT|DARK} · 글자 크기(백분율, PC·휴대폰) · 댓글 기본 정렬 {@code OLDEST|LATEST}.
 *
 * @param textScale       PC 글자 크기
 * @param textScaleMobile 휴대폰 글자 크기(V185)
 * @param own             회원이 저장한 값이 있는가 — false 면 기본값({@code AUTO}·PC {@code 100}·휴대폰 {@code 110}·{@code OLDEST})
 */
public record MemberViewSettingsView(String theme, Integer textScale, Integer textScaleMobile, String commentOrder, boolean own) {
}
