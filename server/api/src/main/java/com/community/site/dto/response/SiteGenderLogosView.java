package com.community.site.dto.response;

/**
 * 성별 규칙의 로고 묶음(V249, docs/features/site-logo-library-contract.md) — 평소에는 성별 로고, 기념일에는 그 성별 로고의 유형에 맞는 기념일 벌(V252)이다.
 * 화면이 로그인한 회원의 성별로 고른다(비로그인·「밝히지 않음」·그 성별 칸이 null 이면 사이트 정보의 {@code logos}).
 *
 * @param male   남성 회원에게 보일 묶음. 정하지 않았으면 null — 기념일에는 {@code logos}(성별 무관 묶음)와 같은 묶음이어도 null
 * @param female 여성 회원에게 보일 묶음. {@code male} 과 같은 규칙
 */
public record SiteGenderLogosView(SiteLogoSetView male, SiteLogoSetView female) {
}
