package com.community.site.dto.request;

import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * {@code POST /api/admin/site/logo-library/bundles} · {@code PUT …/bundles/{id}} — 로고 묶음 만들기·고치기. 검사 순서·칸 경로가 계약이라 Bean Validation 없이 서비스가 본다.
 *
 * @param name       이름 — 앞뒤 공백을 뗀 뒤 1~30자
 * @param nameInLogo 로고에 사이트 이름이 들어 있음(없으면 false)
 * @param cells      칸({@code koLight}·{@code koDark}·{@code enLight}·{@code enDark})마다 넣을 것 —
 *                   {@code {"fileId":"…"}}(먼저 {@code POST /api/admin/files}, purpose=SITE_LOGO 로 올린 파일) · {@code {"svgSource":"…"}} ·
 *                   {@code "KEEP"}(지금 그대로 — 칸을 아예 빼도 같다) · {@code null}(비움). 만들기에서 {@code "KEEP"}·뺀 칸은 빈 칸이다
 */
public record SiteLogoBundleRequest(String name, Boolean nameInLogo, Map<String, JsonNode> cells) {
}
