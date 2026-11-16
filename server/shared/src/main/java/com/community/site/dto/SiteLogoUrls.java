package com.community.site.dto;

/**
 * 사이트 로고 4칸의 {@code <img src>}(V237) — 그림 파일이면 저장소 주소({@code /media/…} 상대), SVG 면 서버가 만든 data URI. 빈 칸은 null 이다.
 * 대체 순서는 화면 규칙({@code UtilSiteLogo.resolve})이라 서버는 채우지 않는다. 관리자 {@code GET /api/admin/site/logos} 와 공개 {@code GET /api/site/info} 의 {@code logos} 가 같은 모양이다.
 *
 * @param koLight ① 한글·밝은 바탕용 — 옛 단일 로고({@code logoUrl})와 같은 값
 * @param koDark  ② 한글·어두운 바탕용
 * @param enLight ③ 영문·밝은 바탕용
 * @param enDark  ④ 영문·어두운 바탕용
 */
public record SiteLogoUrls(String koLight, String koDark, String enLight, String enDark) {

    /** 칸이 모두 빈 값(설정 행이 없을 때) */
    public static final SiteLogoUrls EMPTY = new SiteLogoUrls(null, null, null, null);
}
