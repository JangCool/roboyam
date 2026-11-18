package com.community.site.dto.response;

/**
 * 성별 규칙의 로고 묶음(V249, docs/features/site-logo-library-contract.md) — 기념일이 안 걸린 날에만 사이트 정보에 실린다.
 * 화면이 로그인한 회원의 성별로 고른다(비로그인·「밝히지 않음」·그 성별 칸이 null 이면 사이트 정보의 {@code logos}).
 *
 * @param male   남성 회원에게 보일 묶음. 정하지 않았으면 null
 * @param female 여성 회원에게 보일 묶음. 정하지 않았으면 null
 */
public record SiteGenderLogosView(SiteLogoSetView male, SiteLogoSetView female) {
}
