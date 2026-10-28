package com.community.site.dto;

import com.community.site.dto.response.SiteInfoResponse;
import com.community.site.dto.response.SiteThemeResponse;
import com.community.site.dto.response.WebManifestResponse;

/**
 * 공개 사이트 설정의 한 시점 사본(짧은 시간 메모리에 둔다). 응답 네 곳({@code /api/site/info}·{@code /api/site/theme}·매니페스트·{@code index.html} 머리)이 같은 사본을 쓰므로 서로 어긋나지 않는다.
 *
 * @param theme    지금 테마 + 그 테마의 메뉴 설정({@code /api/site/theme})
 * @param ogCustom 링크 미리보기 이미지를 운영자가 올렸는가(올렸으면 1200×630 PNG 라 크기를 메타에 적는다)
 * @param etag 사본 내용의 지문(따옴표 없는 16진수) — 내용이 바뀌면 바뀐다
 */
public record PublicSiteSnapshot(SiteInfoResponse info, SiteThemeResponse theme, WebManifestResponse manifest, boolean ogCustom, String etag) {
}
