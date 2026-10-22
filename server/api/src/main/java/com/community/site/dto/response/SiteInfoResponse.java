package com.community.site.dto.response;

/**
 * {@code GET /api/site/info}(비로그인) — 공개 사이트가 읽는 사이트 설정. <b>허용한 필드만</b> 담는다(이메일·설치 정보·내부 ID·공개 주소 원문 없음).
 *
 * @param siteName 사이트 이름. 설정 행이 없으면(설치 전) null
 * @param tagline 한 줄 소개. 없으면 null
 * @param logoUrl 로고의 {@code <img src>} — 그림 파일이면 저장소 주소, SVG 로고면 data URI(최대 약 133KB). 없으면 null
 * @param icon 올려 둔 사이트 아이콘. 없으면 null(기본 아이콘)
 * @param ogImageUrl 링크 미리보기 이미지 — 올려 둔 것, 없으면 기본 이미지. 공개 주소가 있으면 절대 주소
 * @param displayDefaults 표시 기본값
 * @param boardDisplay 공개 게시판 표시 기본값(날짜 표시 방식·새 글 기준). 게시판 기본 설정 행이 없으면(설치 전) 처음 값
 */
public record SiteInfoResponse(String siteName, String tagline, String logoUrl, SiteIconResponse icon, String ogImageUrl,
                               SiteDisplayDefaultsView displayDefaults, BoardDisplayView boardDisplay) {
}
