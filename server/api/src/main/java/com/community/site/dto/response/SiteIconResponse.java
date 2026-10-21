package com.community.site.dto.response;

/**
 * 공개 사이트가 쓰는 사이트 아이콘의 주소들 — 운영자가 올린 아이콘이 있을 때만 응답에 든다(없으면 {@link SiteInfoResponse#icon} 이 null, 화면은 기본 아이콘을 쓴다).
 * 주소는 저장소의 공개 주소(로컬 디스크는 {@code /media/…} 상대 주소)이고 {@code version} 은 내용이 바뀌면 바뀐다.
 */
public record SiteIconResponse(String version, String favicon32, String favicon48, String appleTouch, String icon192, String icon512) {
}
