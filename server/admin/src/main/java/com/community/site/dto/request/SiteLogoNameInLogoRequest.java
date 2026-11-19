package com.community.site.dto.request;

/**
 * {@code PUT /api/admin/site/logo-library/bundles/{id}/name-in-logo} — 묶음의 「로고에 사이트 이름이 들어 있음」만 바꾼다(기본 제공 묶음도 된다 — 그림·이름은 그대로 잠겨 있다).
 *
 * @param nameInLogo 켜면 이 묶음이 보일 때 상단 로고 자리가 이름 글자를 붙이지 않는다 — 없으면 {@code fields.nameInLogo}
 */
public record SiteLogoNameInLogoRequest(Boolean nameInLogo) {
}
