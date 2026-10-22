package com.community.site.dto.request;

/**
 * 공개 사이트 테마 저장 요청. 값 검사는 서비스가 한다({@code fields.theme}).
 *
 * @param theme 테마 id — {@code SiteThemes.IDS} 중 하나
 */
public record SiteThemeRequest(String theme) {
}
