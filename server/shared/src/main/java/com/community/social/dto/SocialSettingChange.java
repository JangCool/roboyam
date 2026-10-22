package com.community.social.dto;

/**
 * 소셜 로그인 설정 이력의 바뀐 칸 하나. 값은 이력에 남겨도 되는 모양으로 이미 가린 글자다 — 시크릿은 {@code SET}·{@code CHANGED}·{@code CLEARED} 표시만,
 * 클라이언트 ID 는 끝 4자리만({@code ****abcd}), 켜짐은 {@code true}·{@code false}.
 *
 * @param field  {@code clientId} · {@code clientSecret} · {@code enabled}
 * @param before 만들기면 null
 * @param after  지우기면 null
 */
public record SocialSettingChange(String field, String before, String after) {
}
