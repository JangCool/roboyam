package com.community.site.service;

import java.util.List;

/**
 * 공개 사이트 테마 id 목록 — 서버의 원천이다. user-web 의 테마 레지스트리({@code apps/user-web/app/utils/util-theme.ts} 의 {@code THEMES})와
 * 같은 id 를 둔다({@code .claude/guides/frontend/themes.md}). 새 테마는 화면 레지스트리와 여기에 한 줄씩 더하고, 그 테마의 메뉴 설정 기본값({@link SiteThemeMenus})과
 * 홈 기본 배치({@code HomeLayoutDefaults})도 함께 둔다.
 *
 * <p>운영자는 이 목록 안에서만 고를 수 있고(밖이면 {@code 400 VALIDATION} {@code fields.theme}), 저장된 값이 목록에서 빠졌으면(테마를 걷어 낸 경우)
 * 읽을 때 기본 테마로 본다. 사용자 개인 테마는 없다.
 */
public final class SiteThemes {

    /** 기본 테마 — 설치 직후의 값(V52 의 칸 기본값과 같다), 모르는 값의 폴백 */
    public static final String DEFAULT = "default";

    /** 고를 수 있는 테마 id(화면에 보일 순서) — 기본 · 다음식 · 네이버식 · 로보얌 */
    public static final List<String> IDS = List.of(DEFAULT, "daum", "naver", "roboyam");

    private SiteThemes() {
    }

    public static boolean isKnown(String id) {
        return id != null && IDS.contains(id);
    }

    /** 저장된 값을 쓸 테마 id 로 — 목록에 없거나 비었으면 기본 테마 */
    public static String normalize(String id) {
        return isKnown(id) ? id : DEFAULT;
    }
}
