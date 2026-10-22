package com.community.site.dto.response;

import java.util.List;

/**
 * 공개 사이트 테마 — 지금 값과 고를 수 있는 id 목록. 테마 이름(화면 문구)은 화면이 id 로 i18n 키를 찾는다.
 *
 * @param theme  지금 테마 id(저장된 값이 목록에서 빠졌으면 기본 테마 {@code default})
 * @param themes 고를 수 있는 테마 id(보일 순서)
 */
public record SiteThemeResponse(String theme, List<String> themes) {
}
